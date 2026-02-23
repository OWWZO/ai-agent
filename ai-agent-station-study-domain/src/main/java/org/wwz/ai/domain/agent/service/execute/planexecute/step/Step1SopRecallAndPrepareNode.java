package org.wwz.ai.domain.agent.service.execute.planexecute.step;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.wwz.ai.domain.agent.genie.agent.agent.AgentContext;
import org.wwz.ai.domain.agent.genie.agent.dto.SopRecallResponse;
import org.wwz.ai.domain.agent.genie.agent.printer.Printer;
import org.wwz.ai.domain.agent.genie.agent.printer.SSEPrinter;
import org.wwz.ai.domain.agent.genie.agent.tool.ToolCollection;
import org.wwz.ai.domain.agent.genie.agent.tool.common.*;
import org.wwz.ai.domain.agent.genie.agent.tool.mcp.McpTool;
import org.wwz.ai.domain.agent.genie.agent.util.DateUtil;
import org.wwz.ai.domain.agent.genie.config.GenieConfig;
import org.wwz.ai.domain.agent.genie.model.req.AgentRequest;
import org.wwz.ai.domain.agent.genie.service.SopRecallService;
import org.wwz.ai.domain.agent.service.execute.planexecute.step.factory.DefaultPlanSolveAgentExecuteStrategyFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * PlanSolve 逻辑树 - 步骤1：SOP召回 + 准备 AgentContext 与工具
 */
@Slf4j
@Service
public class Step1SopRecallAndPrepareNode extends AbstractExecuteSupport {

    @Resource
    private GenieConfig genieConfig;

    @Resource
    private SopRecallService sopRecallService;

    @Resource
    private Step2PlanExecuteNode step2PlanExecuteNode;

    @Override
    protected String doApply(AgentRequest request, DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("PlanSolve Step1: SOP recall and prepare for requestId: {}", request.getRequestId());

        Printer printer = new SSEPrinter(
                (SseEmitter) dynamicContext.getEmitter(),
                request,
                request.getAgentType()
        );
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

        agentContext.setToolCollection(buildToolCollection(agentContext, request));
        handleSopRecall(agentContext, request);

        dynamicContext.setAgentContext(agentContext);
        dynamicContext.setStep(1);

        return router(request, dynamicContext);
    }

    private void handleSopRecall(AgentContext agentContext, AgentRequest request) {
        try {
            log.info("{} 开始执行SOP召回", request.getRequestId());
            SopRecallResponse sopResponse = sopRecallService.sopRecall(request.getRequestId(), request.getQuery());
            if (sopRecallService.isValidSopResult(sopResponse)) {
                String sopContent = sopResponse.getData().getChoosed_sop_string();
                String sopMode = sopResponse.getData().getSop_mode();
                log.info("{} SOP召回成功，模式：{}，内容长度：{}", request.getRequestId(), sopMode, sopContent.length());
                if (agentContext.getSopPrompt() != null) {
                    String sopPrompt = agentContext.getSopPrompt().replace("{{sop}}", sopContent);
                    agentContext.setSopPrompt(sopPrompt);
                }
            } else {
                log.warn("{} SOP召回失败或结果无效", request.getRequestId());
            }
        } catch (Exception e) {
            log.error("{} SOP召回处理异常", request.getRequestId(), e);
        }
    }

    private ToolCollection buildToolCollection(AgentContext agentContext, AgentRequest request) {
        ToolCollection toolCollection = new ToolCollection();
        toolCollection.setAgentContext(agentContext);

        if ("dataAgent".equals(request.getOutputStyle())) {
            ReportTool htmlTool = new ReportTool();
            htmlTool.setAgentContext(agentContext);
            toolCollection.addTool(htmlTool);
            DataAnalysisTool dataAnalysisTool = new DataAnalysisTool();
            dataAnalysisTool.setAgentContext(agentContext);
            toolCollection.addTool(dataAnalysisTool);
        } else {
            FileTool fileTool = new FileTool();
            fileTool.setAgentContext(agentContext);
            toolCollection.addTool(fileTool);
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

        try {
            McpTool mcpTool = new McpTool();
            mcpTool.setAgentContext(agentContext);
            for (String mcpServer : genieConfig.getMcpServerUrlArr()) {
                String listToolResult = mcpTool.listTool(mcpServer);
                if (listToolResult.isEmpty()) continue;
                JSONObject resp = JSON.parseObject(listToolResult);
                if (resp.getIntValue("code") != 200) continue;
                JSONArray data = resp.getJSONArray("data");
                if (data == null || data.isEmpty()) continue;
                for (int i = 0; i < data.size(); i++) {
                    JSONObject tool = data.getJSONObject(i);
                    toolCollection.addMcpTool(
                            tool.getString("name"),
                            tool.getString("description"),
                            tool.getString("inputSchema"),
                            mcpServer
                    );
                }
            }
        } catch (Exception e) {
            log.error("{} add mcp tool failed", agentContext.getRequestId(), e);
        }
        return toolCollection;
    }

    @Override
    public StrategyHandler<AgentRequest, DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext, String> get(
            AgentRequest requestParameter,
            DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return step2PlanExecuteNode;
    }
}
