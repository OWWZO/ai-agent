package org.wwz.ai.domain.agent.service.execute.react.step;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.reactor.agent.agent.AgentContext;
import org.wwz.ai.domain.agent.reactor.agent.tool.ToolCollection;
import org.wwz.ai.domain.agent.reactor.agent.tool.common.*;
import org.wwz.ai.domain.agent.reactor.agent.tool.mcp.McpTool;
import org.wwz.ai.domain.agent.reactor.agent.util.DateUtil;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.reactor.agent.printer.Printer;
import org.wwz.ai.domain.agent.reactor.agent.printer.SSEPrinter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.wwz.ai.domain.agent.service.execute.react.step.factory.DefaultReactAgentExecuteStrategyFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * React 逻辑树 - 步骤1：准备上下文与工具（AgentContext、AgentRequest、ToolCollection）
 */
@Slf4j
@Service("reactRootNode")
public class RootNode extends AbstractExecuteSupport {

    @Resource
    private ReactorConfig reactorConfig;

    @Resource
    private RunReactNode step2RunReactNode;

    @Override
    protected String doApply(AgentRequest request, DefaultReactAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("React Step1: Prepare context and tools for requestId: {}", request.getRequestId());

        dynamicContext.setStep(0);
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
        dynamicContext.setAgentContext(agentContext);
        dynamicContext.setStep(1);

        return router(request, dynamicContext);
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
            List<String> agentToolList = Arrays.asList(reactorConfig.getMultiAgentToolListMap()
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
            for (String mcpServer : reactorConfig.getMcpServerUrlArr()) {
                String listToolResult = mcpTool.listTool(mcpServer);
                if (listToolResult.isEmpty()) {
                    log.error("{} mcp server {} invalid", agentContext.getRequestId(), mcpServer);
                    continue;
                }
                JSONObject resp = JSON.parseObject(listToolResult);
                if (resp.getIntValue("code") != 200) {
                    log.error("{} mcp server {} code: {}", agentContext.getRequestId(), mcpServer, resp.getIntValue("code"));
                    continue;
                }
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
    public StrategyHandler<AgentRequest, DefaultReactAgentExecuteStrategyFactory.DynamicContext, String> get(
            AgentRequest requestParameter,
            DefaultReactAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return step2RunReactNode;
    }
}
