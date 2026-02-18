package org.wwz.ai.domain.agent.service.execute.react.step;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.genie.agent.agent.AgentContext;
import org.wwz.ai.domain.agent.genie.agent.tool.ToolCollection;
import org.wwz.ai.domain.agent.genie.agent.tool.common.*;
import org.wwz.ai.domain.agent.genie.agent.tool.mcp.McpTool;
import org.wwz.ai.domain.agent.genie.config.GenieConfig;
import org.wwz.ai.domain.agent.genie.model.req.AgentRequest;
import org.wwz.ai.domain.agent.genie.service.AgentHandlerService;
import org.wwz.ai.domain.agent.genie.service.impl.AgentHandlerFactory;
import org.wwz.ai.domain.agent.model.entity.ExecuteCommandEntity;
import org.wwz.ai.domain.agent.service.execute.react.AbstractExecuteSupport;

import java.util.Arrays;
import java.util.List;

/**
 * React Agent 执行根节点
 * 负责组装工具、调用 Genie 核心 Handler
 */
@Slf4j
@Service("reactRootNode")
public class RootNode extends AbstractExecuteSupport {

    @Resource
    private GenieConfig genieConfig;

    @Resource
    private AgentHandlerFactory agentHandlerFactory;

    @Override
    protected String doApply(ExecuteCommandEntity requestParameter, AgentContext dynamicContext) throws Exception {
        log.info("ReactRootNode start executing for requestId: {}", requestParameter.getRequestId());

        // 1. 构建 AgentRequest
        AgentRequest agentRequest = buildAgentRequest(requestParameter, dynamicContext);

        // 2. 构建工具列表
        dynamicContext.setToolCollection(buildToolCollection(dynamicContext, agentRequest));

        // 3. 获取 Handler
        AgentHandlerService handler = agentHandlerFactory.getHandler(dynamicContext, agentRequest);

        // 4. 执行 Handler
        handler.handle(dynamicContext, agentRequest);

        return "success";
    }

    private AgentRequest buildAgentRequest(ExecuteCommandEntity entity, AgentContext context) {
        AgentRequest request = new AgentRequest();
        request.setRequestId(entity.getRequestId());
        request.setQuery(context.getQuery()); // 使用 Context 中处理过的 query
        request.setAgentType(entity.getAgentType());
        request.setOutputStyle(entity.getOutputStyle());
        request.setIsStream(entity.getIsStream());
        request.setSopPrompt(entity.getSopPrompt());
        request.setBasePrompt(entity.getBasePrompt());
        // Erp/User mapping if needed
        return request;
    }

    /**
     * 构建工具列表 (复用 GenieController 逻辑)
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

    @Override
    public StrategyHandler<ExecuteCommandEntity, AgentContext, String> get(ExecuteCommandEntity requestParameter, AgentContext dynamicContext) throws Exception {
        return null;
    }
}
