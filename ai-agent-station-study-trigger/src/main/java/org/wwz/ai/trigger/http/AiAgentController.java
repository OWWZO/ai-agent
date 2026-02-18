package org.wwz.ai.trigger.http;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.wwz.ai.api.IAiAgentService;
import org.wwz.ai.api.dto.AiAgentResponseDTO;
import org.wwz.ai.api.dto.ArmoryAgentRequestDTO;
import org.wwz.ai.api.dto.ArmoryApiRequestDTO;
import org.wwz.ai.api.dto.AutoAgentRequestDTO;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.domain.agent.genie.agent.agent.AgentContext;
import org.wwz.ai.domain.agent.genie.agent.printer.Printer;
import org.wwz.ai.domain.agent.genie.agent.printer.SSEPrinter;
import org.wwz.ai.domain.agent.genie.agent.tool.ToolCollection;
import org.wwz.ai.domain.agent.genie.agent.tool.common.*;
import org.wwz.ai.domain.agent.genie.agent.tool.mcp.McpTool;
import org.wwz.ai.domain.agent.genie.agent.util.DateUtil;
import org.wwz.ai.domain.agent.genie.agent.util.ThreadUtil;
import org.wwz.ai.domain.agent.genie.config.GenieConfig;
import org.wwz.ai.domain.agent.genie.model.req.AgentRequest;
import org.wwz.ai.domain.agent.genie.service.AgentHandlerService;
import org.wwz.ai.domain.agent.genie.service.impl.AgentHandlerFactory;
import org.wwz.ai.domain.agent.model.entity.ExecuteCommandEntity;
import org.wwz.ai.domain.agent.model.valobj.AiAgentVO;
import org.wwz.ai.domain.agent.service.IAgentDispatchService;
import org.wwz.ai.domain.agent.service.IArmoryService;
import org.wwz.ai.types.enums.ResponseCode;
import com.alibaba.fastjson.JSON;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.wwz.ai.domain.agent.genie.model.req.GptQueryReq;
import org.wwz.ai.domain.agent.genie.service.IGptProcessService;

/**
 * AutoAgent 自动智能对话体
 */
@Slf4j
@RestController
@RequestMapping("/")
public class AiAgentController implements IAiAgentService {

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(5);

    private static final long HEARTBEAT_INTERVAL = 10_000L; // 10秒心跳间隔

    @Autowired
    protected GenieConfig genieConfig;

    @Autowired
    private AgentHandlerFactory agentHandlerFactory;

    @Resource
    private IAgentDispatchService agentDispatchService;

    @Resource
    private IArmoryService armoryService;

    @Resource
    private IGptProcessService gptProcessService;
    /**
     * 开启SSE心跳
     * @param emitter
     * @param requestId
     * @return
     */
    private ScheduledFuture<?> startHeartbeat(SseEmitter emitter, String requestId) {
        return executor.scheduleAtFixedRate(() -> {
            try {
                // 发送心跳消息
                log.info("{} send heartbeat", requestId);
                emitter.send("heartbeat");
            } catch (Exception e) {
                // 发送心跳失败，关闭连接
                log.error("{} heartbeat failed, closing connection", requestId, e);
                emitter.completeWithError(e);
            }
        }, HEARTBEAT_INTERVAL, HEARTBEAT_INTERVAL, TimeUnit.MILLISECONDS);
    }

    /**
     * 注册SSE事件
     * @param emitter
     * @param requestId
     * @param heartbeatFuture
     */
    private void registerSSEMonitor(SseEmitter emitter, String requestId, ScheduledFuture<?> heartbeatFuture) {
        // 监听SSE异常事件
        emitter.onCompletion(() -> {
            log.info("{} SSE connection completed normally", requestId);
            heartbeatFuture.cancel(true);
        });

        // 监听连接超时事件
        emitter.onTimeout(() -> {
            log.info("{} SSE connection timed out", requestId);
            heartbeatFuture.cancel(true);
            emitter.complete();
        });

        // 监听连接错误事件
        emitter.onError((ex) -> {
            log.info("{} SSE connection error: ", requestId, ex);
            heartbeatFuture.cancel(true);
            emitter.completeWithError(ex);
        });
    }

    /**
     * 执行智能体调度
     * @param request
     * @return
     * @throws UnsupportedEncodingException
     */
    @PostMapping("/AutoAgent")
    public SseEmitter AutoAgent(@RequestBody AgentRequest request) throws UnsupportedEncodingException {

        log.info("{} auto agent request: {}", request.getRequestId(), JSON.toJSONString(request));

        Long AUTO_AGENT_SSE_TIMEOUT = 600 * 600 * 1000L;

        SseEmitter emitter = new SseEmitter(AUTO_AGENT_SSE_TIMEOUT);
        // SSE心跳
        ScheduledFuture<?> heartbeatFuture = startHeartbeat(emitter, request.getRequestId());
        // 监听SSE事件
        registerSSEMonitor(emitter, request.getRequestId(), heartbeatFuture);
        // 拼接输出类型
        request.setQuery(handleOutputStyle(request));
        // 执行调度引擎
        ThreadUtil.execute(() -> {
            try {
                Printer printer = new SSEPrinter(emitter, request, request.getAgentType());
                AgentContext agentContext = AgentContext.builder()
                        .requestId(request.getRequestId())
                        .sessionId(request.getRequestId())
                        .printer(printer)
                        .query(request.getQuery())
                        .task("")
                        .dateInfo(DateUtil.CurrentDateInfo())
                        .productFiles(new ArrayList<>())
                        .taskProductFiles(new ArrayList<>())
                        .sopPrompt(request.getSopPrompt())
                        .basePrompt(request.getBasePrompt())
                        .agentType(request.getAgentType())
                        .isStream(Objects.nonNull(request.getIsStream()) ? request.getIsStream() : false)
                        .templateType("dataAgent".equals(request.getOutputStyle()) ? "fix" : "empty")
                        .build();
                // 构建工具列表
                agentContext.setToolCollection(buildToolCollection(agentContext, request));
                // 根据数据类型获取对应的处理器
                AgentHandlerService handler = agentHandlerFactory.getHandler(agentContext, request);
                // 执行处理逻辑
                handler.handle(agentContext, request);
                // 关闭连接
                emitter.complete();

            } catch (Exception e) {
                log.error("{} auto agent error", request.getRequestId(), e);
            }
        });

        return emitter;
    }


    /**
     * html模式： query+以 html展示
     * docs模式：query+以 markdown展示
     * table 模式: query+以 excel 展示
     */
    private String handleOutputStyle(AgentRequest request) {
        String query = request.getQuery();
        Map<String, String> outputStyleMap = genieConfig.getOutputStylePrompts();
        if (!StringUtils.isEmpty(request.getOutputStyle())) {
            query += outputStyleMap.computeIfAbsent(request.getOutputStyle(), k -> "");
        }
        return query;
    }


    /**
     * 构建工具列表
     *
     * @param agentContext
     * @param request
     * @return
     */
    private ToolCollection buildToolCollection(AgentContext agentContext, AgentRequest request) {

        ToolCollection toolCollection = new ToolCollection();
        toolCollection.setAgentContext(agentContext);

        // data agent
        if ("dataAgent".equals(request.getOutputStyle())) {
            ReportTool htmlTool = new ReportTool();
            htmlTool.setAgentContext(agentContext);
            toolCollection.addTool(htmlTool);

            DataAnalysisTool dataAnalysisTool = new DataAnalysisTool();
            dataAnalysisTool.setAgentContext(agentContext);
            toolCollection.addTool(dataAnalysisTool);
        } else {
            // file
            FileTool fileTool = new FileTool();
            fileTool.setAgentContext(agentContext);
            toolCollection.addTool(fileTool);
            // default tool
            List<String> agentToolList = Arrays.asList(genieConfig.getMultiAgentToolListMap()
                    .getOrDefault("default", "search,code,report").split(","));
            if (!agentToolList.isEmpty()) {
                if (agentToolList.contains("code")) {
                    CodeInterpreterTool codeTool = new CodeInterpreterTool();
                    codeTool.setAgentContext(agentContext);
                    toolCollection.addTool(codeTool);
                }
                if (agentToolList.contains("report")) {
                    ReportTool htmlTool = new ReportTool();
                    htmlTool.setAgentContext(agentContext);
                    toolCollection.addTool(htmlTool);
                }
                if (agentToolList.contains("search")) {
                    DeepSearchTool deepSearchTool = new DeepSearchTool();
                    deepSearchTool.setAgentContext(agentContext);
                    toolCollection.addTool(deepSearchTool);
                }
                if (agentToolList.contains("data_analysis")) {
                    DataAnalysisTool dataAnalysisTool = new DataAnalysisTool();
                    dataAnalysisTool.setAgentContext(agentContext);
                    toolCollection.addTool(dataAnalysisTool);
                }
            }
        }

        // mcp tool
        try {
            McpTool mcpTool = new McpTool();
            mcpTool.setAgentContext(agentContext);
            for (String mcpServer : genieConfig.getMcpServerUrlArr()) {
                String listToolResult = mcpTool.listTool(mcpServer);
                if (listToolResult.isEmpty()) {
                    log.error("{} mcp server {} invalid", agentContext.getRequestId(), mcpServer);
                    continue;
                }

                JSONObject resp = JSON.parseObject(listToolResult);
                if (resp.getIntValue("code") != 200) {
                    log.error("{} mcp serve {} code: {}, message: {}", agentContext.getRequestId(), mcpServer,
                            resp.getIntValue("code"), resp.getString("message"));
                    continue;
                }
                JSONArray data = resp.getJSONArray("data");
                if (data.isEmpty()) {
                    log.error("{} mcp serve {} code: {}, message: {}", agentContext.getRequestId(), mcpServer,
                            resp.getIntValue("code"), resp.getString("message"));
                    continue;
                }
                for (int i = 0; i < data.size(); i++) {
                    JSONObject tool = data.getJSONObject(i);
                    String method = tool.getString("name");
                    String description = tool.getString("description");
                    String inputSchema = tool.getString("inputSchema");
                    toolCollection.addMcpTool(method, description, inputSchema, mcpServer);
                }
            }
        } catch (Exception e) {
            log.error("{} add mcp tool failed", agentContext.getRequestId(), e);
        }

        return toolCollection;
    }

    /**
     * 探活接口
     *
     * @return
     */
    @RequestMapping(value = "/web/health", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ok");
    }


    /**
     * 处理Agent流式增量查询请求，返回SSE事件流
     * @param params 查询请求参数对象，包含GPT查询所需信息
     * @return 返回SSE事件发射器，用于流式传输增量响应结果
     */
    @RequestMapping(value = "/web/api/v1/gpt/queryAgentStreamIncr", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter queryAgentStreamIncr(@RequestBody GptQueryReq params) {
        return gptProcessService.queryMultiAgentIncrStream(params);
    }

    @RequestMapping(value = "auto_agent1", method = RequestMethod.POST)
    public ResponseBodyEmitter autoAgent(@RequestBody AutoAgentRequestDTO request, HttpServletResponse response) {
        log.info("AutoAgent流式执行请求开始，请求信息：{}", JSON.toJSONString(request));

        try {
            // 设置SSE响应头
            response.setContentType("text/event-stream");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("Connection", "keep-alive");

            // 1. 创建流式输出对象
            ResponseBodyEmitter emitter = new ResponseBodyEmitter(Long.MAX_VALUE);

            // 2. 构建执行命令实体
            ExecuteCommandEntity executeCommandEntity = ExecuteCommandEntity.builder()
                    .aiAgentId(request.getAiAgentId())
                    .message(request.getMessage())
                    .sessionId(request.getSessionId())
                    .maxStep(request.getMaxStep())
                    .build();

            // 3. 调度处理
            agentDispatchService.dispatch(executeCommandEntity, emitter);

            return emitter;

        } catch (Exception e) {
            log.error("AutoAgent请求处理异常：{}", e.getMessage(), e);
            ResponseBodyEmitter errorEmitter = new ResponseBodyEmitter();
            try {
                errorEmitter.send("请求处理异常：" + e.getMessage());
                errorEmitter.complete();
            } catch (Exception ex) {
                log.error("发送错误信息失败：{}", ex.getMessage(), ex);
            }
            return errorEmitter;
        }
    }


    @RequestMapping(value = "armory_agent", method = RequestMethod.POST)
    @Override
    public Response<Boolean> armoryAgent(@RequestBody ArmoryAgentRequestDTO request) {
        log.info("装配智能体请求开始，请求信息：{}", JSON.toJSONString(request));

        try {
            // 参数校验
            if (request == null || request.getAgentId() == null || request.getAgentId().trim().isEmpty()) {
                log.warn("装配智能体请求参数无效：agentId为空");
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("agentId不能为空")
                        .data(false)
                        .build();
            }
            
            // 调用装配服务
            armoryService.acceptArmoryAgent(request.getAgentId());
            
            log.info("装配智能体成功，agentId：{}", request.getAgentId());
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info("装配成功")
                    .data(true)
                    .build();
                    
        } catch (Exception e) {
            log.error("装配智能体失败，agentId：{}，错误信息：{}", 
                    request != null ? request.getAgentId() : "null", e.getMessage(), e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info("装配失败：" + e.getMessage())
                    .data(false)
                    .build();
        }
    }

    @RequestMapping(value = "query_available_agents", method = RequestMethod.GET)
    @Override
    public Response<List<AiAgentResponseDTO>> queryAvailableAgents() {
        log.info("查询可用智能体列表请求开始");

        try {
            // 调用装配服务查询可用智能体
            List<AiAgentVO> aiAgentVOList = armoryService.queryAvailableAgents();
            
            // 转换为响应DTO
            List<AiAgentResponseDTO> responseList = new ArrayList<>();
            for (AiAgentVO aiAgentVO : aiAgentVOList) {
                AiAgentResponseDTO responseDTO = AiAgentResponseDTO.builder()
                        .agentId(aiAgentVO.getAgentId())
                        .agentName(aiAgentVO.getAgentName())
                        .description(aiAgentVO.getDescription())
                        .channel(aiAgentVO.getChannel())
                        .strategy(aiAgentVO.getStrategy())
                        .status(aiAgentVO.getStatus())
                        .build();
                responseList.add(responseDTO);
            }
            
            log.info("查询可用智能体列表成功，共{}个智能体", responseList.size());
            return Response.<List<AiAgentResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info("查询成功")
                    .data(responseList)
                    .build();
                    
        } catch (Exception e) {
            log.error("查询可用智能体列表失败，错误信息：{}", e.getMessage(), e);
            return Response.<List<AiAgentResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info("查询失败：" + e.getMessage())
                    .data(new ArrayList<>())
                    .build();
        }
    }

    @RequestMapping(value = "armory_api", method = RequestMethod.POST)
    @Override
    public Response<Boolean> armoryApi(@RequestBody ArmoryApiRequestDTO request) {
        log.info("装配API请求开始，请求信息：{}", JSON.toJSONString(request));

        try {
            // 参数校验
            if (request == null || request.getApiId() == null || request.getApiId().trim().isEmpty()) {
                log.warn("装配API请求参数无效：apiId为空");
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("apiId不能为空")
                        .data(false)
                        .build();
            }
            
            // 调用装配服务
            armoryService.acceptArmoryAgentClientModelApi(request.getApiId());
            
            log.info("装配API成功，apiId：{}", request.getApiId());
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info("装配成功")
                    .data(true)
                    .build();
                    
        } catch (Exception e) {
            log.error("装配API失败，apiId：{}，错误信息：{}", 
                    request != null ? request.getApiId() : "null", e.getMessage(), e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info("装配失败：" + e.getMessage())
                    .data(false)
                    .build();
        }
    }

}
