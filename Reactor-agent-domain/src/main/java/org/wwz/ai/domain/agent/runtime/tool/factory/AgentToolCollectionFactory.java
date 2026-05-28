package org.wwz.ai.domain.agent.runtime.tool.factory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.dto.tool.McpToolInfo;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.tool.common.CodeInterpreterTool;
import org.wwz.ai.domain.agent.runtime.tool.common.DataAnalysisTool;
import org.wwz.ai.domain.agent.runtime.tool.common.DeepSearchTool;
import org.wwz.ai.domain.agent.runtime.tool.common.FileTool;
import org.wwz.ai.domain.agent.runtime.tool.common.ImageGenerationTool;
import org.wwz.ai.domain.agent.runtime.tool.common.MultiModalAgent;
import org.wwz.ai.domain.agent.runtime.tool.common.ReportTool;
import org.wwz.ai.domain.agent.runtime.tool.common.WebFetchTool;
import org.wwz.ai.domain.agent.runtime.tool.common.skill.GlobTool;
import org.wwz.ai.domain.agent.runtime.tool.common.skill.GrepTool;
import org.wwz.ai.domain.agent.runtime.tool.common.skill.ListDirectoryTool;
import org.wwz.ai.domain.agent.runtime.tool.common.skill.ReadTool;
import org.wwz.ai.domain.agent.runtime.tool.common.skill.ScriptRunnerTool;
import org.wwz.ai.domain.agent.runtime.tool.common.skill.SkillTool;
import org.wwz.ai.domain.agent.runtime.tool.mcp.runtime.McpToolExecutor;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRegistry;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRuntimeOptions;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillScriptRunnerClient;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;

import java.util.Arrays;
import java.util.List;

/**
 * 统一构建 PlanSolve / ReAct 的工具集合，避免节点层重复拼装。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentToolCollectionFactory {

    private final ReactorConfig reactorConfig;
    private final McpToolExecutor mcpToolExecutor;
    private final SkillRegistry skillRegistry;
    private final SkillRuntimeOptions skillRuntimeOptions;
    private final SkillScriptRunnerClient skillScriptRunnerClient;

    public ToolCollection buildForReact(AgentContext agentContext, AgentRequest request) {
        return build(agentContext, request, SkillAttachScope.REACT);
    }

    public ToolCollection buildForPlanSolve(AgentContext agentContext, AgentRequest request) {
        return build(agentContext, request, SkillAttachScope.PLAN_SOLVE);
    }

    public ToolCollection buildForParallelTask(AgentContext agentContext,
                                               AgentRequest request,
                                               ToolCollection parentToolCollection) {
        ToolCollection childToolCollection = buildForPlanSolve(agentContext, request);
        if (parentToolCollection != null) {
            childToolCollection.restoreTaskScopedState(parentToolCollection.snapshotTaskScopedState());
        }
        return childToolCollection;
    }

    private ToolCollection build(AgentContext agentContext, AgentRequest request, SkillAttachScope attachScope) {
        ReactorRuntimeDependencies runtimeDependencies = requireRuntimeDependencies(agentContext);
        ToolCollection toolCollection = new ToolCollection();
        toolCollection.setAgentContext(agentContext);
        toolCollection.setMcpToolExecutor(runtimeDependencies.getOptionalMcpToolExecutor());

        if ("dataAgent".equals(request.getOutputStyle())) {
            ReportTool reportTool = new ReportTool();
            reportTool.setAgentContext(agentContext);
            toolCollection.addTool(reportTool);

            DataAnalysisTool dataAnalysisTool = new DataAnalysisTool();
            dataAnalysisTool.setAgentContext(agentContext);
            toolCollection.addTool(dataAnalysisTool);
        } else {
            FileTool fileTool = new FileTool();
            fileTool.setAgentContext(agentContext);
            toolCollection.addTool(fileTool);

            List<String> agentToolList = Arrays.stream(reactorConfig.getMultiAgentToolListMap()
                            .getOrDefault("default", "search,web_fetch,code,report,multimodalagent")
                            .split(","))
                    .map(String::trim)
                    .filter(item -> !item.isEmpty())
                    .toList();

            if (agentToolList.contains("code")) {
                CodeInterpreterTool codeInterpreterTool = new CodeInterpreterTool();
                codeInterpreterTool.setAgentContext(agentContext);
                toolCollection.addTool(codeInterpreterTool);
            }
            if (agentToolList.contains("report")) {
                ReportTool reportTool = new ReportTool();
                reportTool.setAgentContext(agentContext);
                toolCollection.addTool(reportTool);
            }
            if (agentToolList.contains("search")) {
                DeepSearchTool deepSearchTool = new DeepSearchTool();
                deepSearchTool.setAgentContext(agentContext);
                toolCollection.addTool(deepSearchTool);
            }
            if (agentToolList.contains("web_fetch")) {
                WebFetchTool webFetchTool = new WebFetchTool();
                webFetchTool.setAgentContext(agentContext);
                toolCollection.addTool(webFetchTool);
            }
            if (agentToolList.contains("multimodalagent")) {
                MultiModalAgent multiModalAgent = new MultiModalAgent();
                multiModalAgent.setAgentContext(agentContext);
                toolCollection.addTool(multiModalAgent);
            }
            if (agentToolList.contains("image_generation")) {
                ImageGenerationTool imageGenerationTool = new ImageGenerationTool();
                imageGenerationTool.setAgentContext(agentContext);
                toolCollection.addTool(imageGenerationTool);
            }
            if (agentToolList.contains("data_analysis")) {
                DataAnalysisTool dataAnalysisTool = new DataAnalysisTool();
                dataAnalysisTool.setAgentContext(agentContext);
                toolCollection.addTool(dataAnalysisTool);
            }
            if (shouldAttachSkillTools(attachScope)) {
                registerSkillTools(toolCollection, agentContext);
            }
        }

        try {
            for (McpToolInfo toolInfo : mcpToolExecutor.discoverConfiguredTools()) {
                toolCollection.addMcpTool(toolInfo);
            }
        } catch (Exception e) {
            log.error("{} add mcp tool failed", agentContext.getRequestId(), e);
        }
        return toolCollection;
    }

    private ReactorRuntimeDependencies requireRuntimeDependencies(AgentContext agentContext) {
        if (agentContext == null || agentContext.getRuntimeDependencies() == null) {
            throw new IllegalStateException("AgentToolCollectionFactory 缺少 ReactorRuntimeDependencies");
        }
        return agentContext.getRuntimeDependencies();
    }

    private boolean shouldAttachSkillTools(SkillAttachScope attachScope) {
        if (!skillRegistry.isEnabled() || skillRegistry.listSkills().isEmpty()) {
            return false;
        }
        return switch (attachScope) {
            case REACT -> skillRuntimeOptions.isReactEnabled();
            case PLAN_SOLVE -> skillRuntimeOptions.isPlanSolveEnabled();
        };
    }

    private void registerSkillTools(ToolCollection toolCollection, AgentContext agentContext) {
        SkillTool skillTool = new SkillTool(skillRegistry);
        skillTool.setAgentContext(agentContext);
        toolCollection.addTool(skillTool);

        ReadTool readTool = new ReadTool(skillRegistry, skillRuntimeOptions);
        readTool.setAgentContext(agentContext);
        toolCollection.addTool(readTool);

        ListDirectoryTool listDirectoryTool = new ListDirectoryTool(skillRegistry, skillRuntimeOptions);
        listDirectoryTool.setAgentContext(agentContext);
        toolCollection.addTool(listDirectoryTool);

        GlobTool globTool = new GlobTool(skillRegistry, skillRuntimeOptions);
        globTool.setAgentContext(agentContext);
        toolCollection.addTool(globTool);

        GrepTool grepTool = new GrepTool(skillRegistry, skillRuntimeOptions);
        grepTool.setAgentContext(agentContext);
        toolCollection.addTool(grepTool);

        ScriptRunnerTool scriptRunnerTool = new ScriptRunnerTool(skillRegistry, skillRuntimeOptions, skillScriptRunnerClient);
        scriptRunnerTool.setAgentContext(agentContext);
        toolCollection.addTool(scriptRunnerTool);
    }

    private enum SkillAttachScope {
        REACT,
        PLAN_SOLVE
    }
}
