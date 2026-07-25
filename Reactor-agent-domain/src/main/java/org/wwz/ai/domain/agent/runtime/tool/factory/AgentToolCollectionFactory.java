package org.wwz.ai.domain.agent.runtime.tool.factory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.dto.tool.McpToolInfo;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentRegistry;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentRunner;
import org.wwz.ai.domain.agent.runtime.tool.common.AgentDispatchTool;
import org.wwz.ai.domain.agent.runtime.tool.common.CodeInterpreterTool;
import org.wwz.ai.domain.agent.runtime.askuser.PendingUserQuestionRegistry;
import org.wwz.ai.domain.agent.runtime.planmode.PendingPlanApprovalRegistry;
import org.wwz.ai.domain.agent.runtime.planmode.PlanArtifactStore;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.AskUserQuestionTool;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.EnterPlanModeTool;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.ExitPlanModeTool;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.TaskCreateTool;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.TaskGetTool;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.TaskListTool;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.TaskStopTool;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.TaskUpdateTool;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.TodoWriteTool;
import org.wwz.ai.domain.agent.runtime.tool.common.DataAnalysisTool;
import org.wwz.ai.domain.agent.runtime.tool.common.DeepSearchTool;
import org.wwz.ai.domain.agent.runtime.tool.common.FileTool;
import org.wwz.ai.domain.agent.runtime.tool.common.ImageGenerationTool;
import org.wwz.ai.domain.agent.runtime.tool.common.MultiModalAgent;
import org.wwz.ai.domain.agent.runtime.tool.common.ReportTool;
import org.wwz.ai.domain.agent.runtime.tool.common.WebFetchTool;
import org.wwz.ai.domain.agent.runtime.tool.common.skill.ScriptRunnerTool;
import org.wwz.ai.domain.agent.runtime.tool.common.skill.SkillTool;
import org.wwz.ai.domain.agent.runtime.tool.mcp.runtime.McpToolExecutor;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRegistry;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRuntimeOptions;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillScriptRunnerClient;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceGlobTool;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceGrepTool;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceListTool;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceReadTool;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceRuntimeOptions;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceService;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceEditTool;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceWriteTool;
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
    private final WorkspaceService workspaceService;
    private final WorkspaceRuntimeOptions workspaceRuntimeOptions;
    private final SubAgentRunner subAgentRunner;
    private final SubAgentRegistry subAgentRegistry;
    private final PendingUserQuestionRegistry pendingUserQuestionRegistry;
    private final PendingPlanApprovalRegistry pendingPlanApprovalRegistry;
    private final PlanArtifactStore planArtifactStore;

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
        ensureWorkspaceRoot(agentContext);
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
            // workspace 启用时：agent 用 cwd 系工具感知文件；file_tool 仅作内部适配，不再暴露给 LLM
            if (workspaceService.isEnabled()) {
                registerWorkspaceTools(toolCollection, agentContext);
            } else {
                FileTool fileTool = new FileTool();
                fileTool.setAgentContext(agentContext);
                toolCollection.addTool(fileTool);
            }

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

        // 主 Agent 可派发同步子 Agent；dataAgent 场景不挂载
        if (!"dataAgent".equals(request.getOutputStyle()) && subAgentRunner != null && subAgentRegistry != null) {
            AgentDispatchTool agentDispatchTool = new AgentDispatchTool(subAgentRunner, subAgentRegistry);
            agentDispatchTool.setAgentContext(agentContext);
            toolCollection.addTool(agentDispatchTool);
        }

        // Task / Plan Mode 工具（对标 cc-haha Task* + Enter/ExitPlanMode）
        if (!"dataAgent".equals(request.getOutputStyle())) {
            registerPlanModeTools(toolCollection, agentContext);
        }
        return toolCollection;
    }

    private void registerPlanModeTools(ToolCollection toolCollection, AgentContext agentContext) {
        TaskCreateTool taskCreateTool = new TaskCreateTool();
        taskCreateTool.setAgentContext(agentContext);
        toolCollection.addTool(taskCreateTool);

        TaskGetTool taskGetTool = new TaskGetTool();
        taskGetTool.setAgentContext(agentContext);
        toolCollection.addTool(taskGetTool);

        TaskUpdateTool taskUpdateTool = new TaskUpdateTool();
        taskUpdateTool.setAgentContext(agentContext);
        toolCollection.addTool(taskUpdateTool);

        TaskListTool taskListTool = new TaskListTool();
        taskListTool.setAgentContext(agentContext);
        toolCollection.addTool(taskListTool);

        TodoWriteTool todoWriteTool = new TodoWriteTool();
        todoWriteTool.setAgentContext(agentContext);
        toolCollection.addTool(todoWriteTool);

        TaskStopTool taskStopTool = new TaskStopTool();
        taskStopTool.setAgentContext(agentContext);
        toolCollection.addTool(taskStopTool);

        EnterPlanModeTool enterPlanModeTool = new EnterPlanModeTool(planArtifactStore);
        enterPlanModeTool.setAgentContext(agentContext);
        toolCollection.addTool(enterPlanModeTool);

        ExitPlanModeTool exitPlanModeTool = new ExitPlanModeTool(pendingPlanApprovalRegistry, planArtifactStore);
        exitPlanModeTool.setAgentContext(agentContext);
        toolCollection.addTool(exitPlanModeTool);

        if (pendingUserQuestionRegistry != null) {
            AskUserQuestionTool askUserQuestionTool = new AskUserQuestionTool(pendingUserQuestionRegistry);
            askUserQuestionTool.setAgentContext(agentContext);
            toolCollection.addTool(askUserQuestionTool);
        }
    }

    private void ensureWorkspaceRoot(AgentContext agentContext) {
        if (agentContext == null || !workspaceService.isEnabled()) {
            return;
        }
        if (agentContext.getWorkspaceRoot() != null && !agentContext.getWorkspaceRoot().isBlank()) {
            return;
        }
        try {
            agentContext.setWorkspaceRoot(workspaceService.resolveAndEnsureRoot(agentContext.getSessionId()).toString());
        } catch (Exception e) {
            log.warn("{} ensure workspace root failed", agentContext.getRequestId(), e);
        }
    }

    private void registerWorkspaceTools(ToolCollection toolCollection, AgentContext agentContext) {
        WorkspaceReadTool readTool = new WorkspaceReadTool(workspaceService, workspaceRuntimeOptions);
        readTool.setAgentContext(agentContext);
        toolCollection.addTool(readTool);

        WorkspaceWriteTool writeTool = new WorkspaceWriteTool(workspaceService, workspaceRuntimeOptions);
        writeTool.setAgentContext(agentContext);
        toolCollection.addTool(writeTool);

        WorkspaceEditTool editTool = new WorkspaceEditTool(workspaceService, workspaceRuntimeOptions);
        editTool.setAgentContext(agentContext);
        toolCollection.addTool(editTool);

        WorkspaceListTool listTool = new WorkspaceListTool(workspaceService, workspaceRuntimeOptions);
        listTool.setAgentContext(agentContext);
        toolCollection.addTool(listTool);

        WorkspaceGlobTool globTool = new WorkspaceGlobTool(workspaceService, workspaceRuntimeOptions);
        globTool.setAgentContext(agentContext);
        toolCollection.addTool(globTool);

        WorkspaceGrepTool grepTool = new WorkspaceGrepTool(workspaceService, workspaceRuntimeOptions);
        grepTool.setAgentContext(agentContext);
        toolCollection.addTool(grepTool);
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
        // path 浏览统一走 workspace_*；skill 目录已并入 workspace 可读根
        SkillTool skillTool = new SkillTool(skillRegistry);
        skillTool.setAgentContext(agentContext);
        toolCollection.addTool(skillTool);

        ScriptRunnerTool scriptRunnerTool = new ScriptRunnerTool(skillRegistry, skillRuntimeOptions, skillScriptRunnerClient);
        scriptRunnerTool.setAgentContext(agentContext);
        toolCollection.addTool(scriptRunnerTool);
    }

    private enum SkillAttachScope {
        REACT,
        PLAN_SOLVE
    }
}