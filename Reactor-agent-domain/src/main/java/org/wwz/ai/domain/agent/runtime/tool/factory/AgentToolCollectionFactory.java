package org.wwz.ai.domain.agent.runtime.tool.factory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.dto.tool.McpToolInfo;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.cancel.ActiveAgentRunRegistry;
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
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.SendMessageTool;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.TaskOutputTool;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.TaskStopTool;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.TaskUpdateTool;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.TodoWriteTool;
import org.wwz.ai.domain.agent.runtime.tool.common.DataAnalysisTool;
import org.wwz.ai.domain.agent.runtime.tool.common.CodeExecutionTool;
import org.wwz.ai.domain.agent.runtime.tool.common.DeepSearchTool;
import org.wwz.ai.domain.agent.runtime.tool.common.FileTool;
import org.wwz.ai.domain.agent.runtime.tool.common.ImageGenerationTool;
import org.wwz.ai.domain.agent.runtime.tool.common.MultiModalAgent;
import org.wwz.ai.domain.agent.runtime.tool.common.ReportTool;
import org.wwz.ai.domain.agent.runtime.tool.common.docgen.ChartGeneratorTool;
import org.wwz.ai.domain.agent.runtime.tool.common.docgen.ChecklistGenerateTool;
import org.wwz.ai.domain.agent.runtime.tool.common.docgen.DocumentGenerateTool;
import org.wwz.ai.domain.agent.runtime.tool.common.docgen.DocumentTemplateTool;
import org.wwz.ai.domain.agent.runtime.tool.common.docgen.ExcelGeneratorTool;
import org.wwz.ai.domain.agent.runtime.tool.common.docgen.SlidesGenerateTool;
import org.wwz.ai.domain.agent.runtime.tool.common.docgen.TemplateFillerTool;
import org.wwz.ai.domain.agent.runtime.tool.common.docgen.ThemeDesignerTool;
import org.wwz.ai.domain.agent.runtime.tool.common.dataprep.DataAggregateTool;
import org.wwz.ai.domain.agent.runtime.tool.common.dataprep.DataCleanTool;
import org.wwz.ai.domain.agent.runtime.tool.common.dataprep.DataMergeTool;
import org.wwz.ai.domain.agent.runtime.tool.common.dataprep.DataTransformTool;
import org.wwz.ai.domain.agent.runtime.tool.common.dataprep.DataValidateTool;
import org.wwz.ai.domain.agent.runtime.tool.common.dataprep.SqlQueryTool;
import org.wwz.ai.domain.agent.runtime.tool.common.docread.CitationExtractorTool;
import org.wwz.ai.domain.agent.runtime.tool.common.docread.CsvProcessorTool;
import org.wwz.ai.domain.agent.runtime.tool.common.docread.ExcelReaderTool;
import org.wwz.ai.domain.agent.runtime.tool.common.docread.HtmlProcessorTool;
import org.wwz.ai.domain.agent.runtime.tool.common.docread.ImageOcrTool;
import org.wwz.ai.domain.agent.runtime.tool.common.docread.MarkdownProcessorTool;
import org.wwz.ai.domain.agent.runtime.tool.common.docread.PdfReaderTool;
import org.wwz.ai.domain.agent.runtime.tool.common.docread.PdfStructureTool;
import org.wwz.ai.domain.agent.runtime.tool.common.docread.TextProcessorTool;
import org.wwz.ai.domain.agent.runtime.tool.common.docread.WordReaderTool;
import org.wwz.ai.domain.agent.runtime.tool.common.canvas.CanvasPublishTool;
import org.wwz.ai.domain.agent.runtime.tool.common.canvas.EmitUiPatchTool;
import org.wwz.ai.domain.agent.runtime.tool.common.canvas.EmitUiTreeTool;
import org.wwz.ai.domain.agent.runtime.tool.common.canvas.GetGenuiGuideTool;
import org.wwz.ai.domain.agent.runtime.tool.common.canvas.GetHtmlCanvasGuideTool;
import org.wwz.ai.domain.agent.runtime.tool.common.canvas.ListUiComponentsTool;
import org.wwz.ai.domain.agent.runtime.tool.common.MemoryTool;
import org.wwz.ai.domain.agent.runtime.tool.common.SessionSearchTool;
import org.wwz.ai.domain.agent.runtime.tool.common.WebFetchTool;
import org.wwz.ai.domain.agent.runtime.tool.common.WebSearchTool;
import org.wwz.ai.domain.agent.runtime.tool.common.mcp.ListMcpResourcesTool;
import org.wwz.ai.domain.agent.runtime.tool.common.mcp.ReadMcpResourceTool;
import org.wwz.ai.domain.agent.runtime.tool.common.mcp.ToolSearchTool;
import org.wwz.ai.domain.agent.runtime.tool.common.shell.BashTool;
import org.wwz.ai.domain.agent.runtime.tool.common.skill.SkillAuthorTool;
import org.wwz.ai.domain.agent.runtime.tool.common.skill.SkillTool;
import org.wwz.ai.domain.agent.runtime.tool.mcp.runtime.DeferredMcpCatalog;
import org.wwz.ai.domain.agent.runtime.tool.mcp.runtime.McpToolExecutor;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillMaterializer;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillPackageService;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRegistry;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRuntimeLayout;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRuntimeOptions;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillVirtualPaths;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

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
    private final SkillRuntimeLayout skillRuntimeLayout;
    private final SkillMaterializer skillMaterializer;
    private final SkillVirtualPaths skillVirtualPaths;
    private final SkillPackageService skillPackageService;
    private final WorkspaceService workspaceService;
    private final WorkspaceRuntimeOptions workspaceRuntimeOptions;
    private final SubAgentRunner subAgentRunner;
    private final SubAgentRegistry subAgentRegistry;
    private final ActiveAgentRunRegistry activeAgentRunRegistry;
    private final PendingUserQuestionRegistry pendingUserQuestionRegistry;
    private final PendingPlanApprovalRegistry pendingPlanApprovalRegistry;
    private final PlanArtifactStore planArtifactStore;

    public ToolCollection buildForReact(AgentContext agentContext, AgentRequest request) {
        return build(agentContext, request, SkillAttachScope.REACT);
    }

    public ToolCollection buildForPlanSolve(AgentContext agentContext, AgentRequest request) {
        return build(agentContext, request, SkillAttachScope.PLAN_SOLVE);
    }

    /**
     * 生成 PlanSolve 主 Agent 的可见工具池；完整工具池仍由子 Agent 继承。
     */
    public ToolCollection filterForPlanSolveMain(ToolCollection fullToolCollection) {
        ToolCollection main = new ToolCollection();
        if (fullToolCollection == null) {
            return main;
        }
        main.setMcpToolExecutor(fullToolCollection.getMcpToolExecutor());
        Set<String> allowed = new HashSet<>(parseToolNames(reactorConfig.getPlanSolveMainToolList()));
        if (fullToolCollection.getToolMap() != null) {
            fullToolCollection.getToolMap().forEach((name, tool) -> {
                if (allowed.contains(name)) {
                    main.addTool(tool);
                }
            });
        }
        if (fullToolCollection.getMcpToolMap() != null) {
            fullToolCollection.getMcpToolMap().forEach((name, tool) -> {
                // 显式白名单；alwaysLoad MCP 在启用 ToolSearch 时对主 Agent 可见
                if (allowed.contains(name)
                        || allowed.contains("*")
                        || (Boolean.TRUE.equals(tool.getAlwaysLoad()) && allowed.contains("ToolSearch"))) {
                    main.addMcpTool(tool);
                }
            });
        }
        main.restoreTaskScopedState(fullToolCollection.snapshotTaskScopedState());
        return main;
    }

    private ToolCollection build(AgentContext agentContext, AgentRequest request, SkillAttachScope attachScope) {
        // 工具集合是一次请求的能力快照：先绑定上下文和工作区，再按 outputStyle、
        // 配置白名单及 attachScope 装配工具，最后补 MCP、子 Agent 和 Plan Mode 能力。
        // 所有工具复用同一 AgentContext，才能保持 artifact、取消和 ledger 关联。
        ReactorRuntimeDependencies runtimeDependencies = requireRuntimeDependencies(agentContext);
        ensureWorkspaceRoot(agentContext);
        ToolCollection toolCollection = new ToolCollection();
        toolCollection.setAgentContext(agentContext);
        toolCollection.setMcpToolExecutor(runtimeDependencies.getOptionalMcpToolExecutor());

        // 长期记忆工具（有界策展）；依赖 LTM 装配时可用
        if (runtimeDependencies.getOptionalCuratedMemoryStore() != null
                || runtimeDependencies.getOptionalLtmManager() != null) {
            MemoryTool memoryTool = new MemoryTool();
            addTool(toolCollection, memoryTool, agentContext, MemoryTool::setAgentContext);
        }
        if (runtimeDependencies.getOptionalSessionSearchService() != null) {
            SessionSearchTool sessionSearchTool = new SessionSearchTool();
            addTool(toolCollection, sessionSearchTool, agentContext, SessionSearchTool::setAgentContext);
        }

        // dataAgent 只暴露问数工具；普通 Agent 按配置装配 workspace、文档、数据处理、画布和外部检索工具。
        if ("dataAgent".equals(request.getOutputStyle())) {
            // dataAgent 只暴露问数入口，避免把普通 Agent 的文件/检索/编排工具带入
            // 数据查询协议；其它模式再按配置逐组挂载能力。
            DataAnalysisTool dataAnalysisTool = new DataAnalysisTool();
            addTool(toolCollection, dataAnalysisTool, agentContext, DataAnalysisTool::setAgentContext);
        } else {
            // workspace 启用时：agent 用 cwd 系工具感知文件；file_tool 仅作内部适配，不再暴露给 LLM
            if (workspaceService.isEnabled()) {
                registerWorkspaceTools(toolCollection, agentContext);
            } else {
                FileTool fileTool = new FileTool();
                addTool(toolCollection, fileTool, agentContext, FileTool::setAgentContext);
            }

            List<String> agentToolList = parseToolNames(reactorConfig.getMultiAgentToolListMap()
                            .getOrDefault("default", "search,web_fetch,web_search,code,code_execution,report,docgen,docread,dataprep,canvas,multimodalagent,image_generation,data_analysis")
                    );

            if (agentToolList.contains("code")) {
                CodeInterpreterTool codeInterpreterTool = new CodeInterpreterTool();
                addTool(toolCollection, codeInterpreterTool, agentContext, CodeInterpreterTool::setAgentContext);
            }
            if (agentToolList.contains("report")) {
                ReportTool reportTool = new ReportTool();
                addTool(toolCollection, reportTool, agentContext, ReportTool::setAgentContext);
            }

            if (agentToolList.contains("docgen")
                    || agentToolList.contains("document_generate")
                    || agentToolList.contains("slides_generate")
                    || agentToolList.contains("excel_generator")
                    || agentToolList.contains("checklist_generate")
                    || agentToolList.contains("template_filler")
                    || agentToolList.contains("document_template")
                    || agentToolList.contains("theme_designer")
                    || agentToolList.contains("chart_generator")) {
                DocumentGenerateTool documentGenerateTool = new DocumentGenerateTool();
                addTool(toolCollection, documentGenerateTool, agentContext, DocumentGenerateTool::setAgentContext);

                SlidesGenerateTool slidesGenerateTool = new SlidesGenerateTool();
                addTool(toolCollection, slidesGenerateTool, agentContext, SlidesGenerateTool::setAgentContext);

                ExcelGeneratorTool excelGeneratorTool = new ExcelGeneratorTool();
                addTool(toolCollection, excelGeneratorTool, agentContext, ExcelGeneratorTool::setAgentContext);

                ChecklistGenerateTool checklistGenerateTool = new ChecklistGenerateTool();
                addTool(toolCollection, checklistGenerateTool, agentContext, ChecklistGenerateTool::setAgentContext);

                TemplateFillerTool templateFillerTool = new TemplateFillerTool();
                addTool(toolCollection, templateFillerTool, agentContext, TemplateFillerTool::setAgentContext);

                DocumentTemplateTool documentTemplateTool = new DocumentTemplateTool();
                addTool(toolCollection, documentTemplateTool, agentContext, DocumentTemplateTool::setAgentContext);

                ThemeDesignerTool themeDesignerTool = new ThemeDesignerTool();
                addTool(toolCollection, themeDesignerTool, agentContext, ThemeDesignerTool::setAgentContext);

                ChartGeneratorTool chartGeneratorTool = new ChartGeneratorTool();
                addTool(toolCollection, chartGeneratorTool, agentContext, ChartGeneratorTool::setAgentContext);
            }
            if (agentToolList.contains("docread")
                    || agentToolList.contains("csv_processor")
                    || agentToolList.contains("excel_reader")
                    || agentToolList.contains("pdf_reader")
                    || agentToolList.contains("word_reader")
                    || agentToolList.contains("html_processor")
                    || agentToolList.contains("markdown_processor")
                    || agentToolList.contains("text_processor")
                    || agentToolList.contains("pdf_structure")
                    || agentToolList.contains("citation_extractor")
                    || agentToolList.contains("image_ocr")) {
                registerDocReadTools(toolCollection, agentContext);
            }
            if (agentToolList.contains("dataprep")
                    || agentToolList.contains("data_aggregate")
                    || agentToolList.contains("data_clean")
                    || agentToolList.contains("data_merge")
                    || agentToolList.contains("data_transform")
                    || agentToolList.contains("data_validate")
                    || agentToolList.contains("sql_query")) {
                registerDataPrepTools(toolCollection, agentContext);
            }
            if (agentToolList.contains("canvas")
                    || agentToolList.contains("canvas_publish")
                    || agentToolList.contains("html_canvas")
                    || agentToolList.contains("genui")) {
                GetHtmlCanvasGuideTool getHtmlCanvasGuideTool = new GetHtmlCanvasGuideTool();
                addTool(toolCollection, getHtmlCanvasGuideTool, agentContext, GetHtmlCanvasGuideTool::setAgentContext);

                CanvasPublishTool canvasPublishTool = new CanvasPublishTool();
                addTool(toolCollection, canvasPublishTool, agentContext, CanvasPublishTool::setAgentContext);

                GetGenuiGuideTool getGenuiGuideTool = new GetGenuiGuideTool();
                addTool(toolCollection, getGenuiGuideTool, agentContext, GetGenuiGuideTool::setAgentContext);

                ListUiComponentsTool listUiComponentsTool = new ListUiComponentsTool();
                addTool(toolCollection, listUiComponentsTool, agentContext, ListUiComponentsTool::setAgentContext);

                EmitUiTreeTool emitUiTreeTool = new EmitUiTreeTool();
                addTool(toolCollection, emitUiTreeTool, agentContext, EmitUiTreeTool::setAgentContext);

                EmitUiPatchTool emitUiPatchTool = new EmitUiPatchTool();
                addTool(toolCollection, emitUiPatchTool, agentContext, EmitUiPatchTool::setAgentContext);
            }
            if (agentToolList.contains("search")) {
                DeepSearchTool deepSearchTool = new DeepSearchTool();
                addTool(toolCollection, deepSearchTool, agentContext, DeepSearchTool::setAgentContext);
            }
            if (agentToolList.contains("web_fetch") || agentToolList.contains("WebFetch")) {
                WebFetchTool webFetchTool = new WebFetchTool();
                addTool(toolCollection, webFetchTool, agentContext, WebFetchTool::setAgentContext);
            }
            if (agentToolList.contains("web_search") || agentToolList.contains("WebSearch")) {
                WebSearchTool webSearchTool = new WebSearchTool();
                addTool(toolCollection, webSearchTool, agentContext, WebSearchTool::setAgentContext);
            }
            if (agentToolList.contains("code_execution")) {
                CodeExecutionTool codeExecutionTool = new CodeExecutionTool();
                addTool(toolCollection, codeExecutionTool, agentContext, CodeExecutionTool::setAgentContext);
            }
            if (agentToolList.contains("multimodalagent")) {
                MultiModalAgent multiModalAgent = new MultiModalAgent();
                addTool(toolCollection, multiModalAgent, agentContext, MultiModalAgent::setAgentContext);
            }
            if (agentToolList.contains("image_generation")) {
                ImageGenerationTool imageGenerationTool = new ImageGenerationTool();
                addTool(toolCollection, imageGenerationTool, agentContext, ImageGenerationTool::setAgentContext);
            }
            if (agentToolList.contains("data_analysis")) {
                DataAnalysisTool dataAnalysisTool = new DataAnalysisTool();
                addTool(toolCollection, dataAnalysisTool, agentContext, DataAnalysisTool::setAgentContext);
            }
            if (shouldAttachSkillTools(attachScope)) {
                registerSkillTools(toolCollection, agentContext);
            }
        }

        attachMcpSurface(toolCollection, agentContext, request);
        applySessionCapabilityFilter(toolCollection, agentContext);

        // 主 Agent 可派发子 Agent；dataAgent 场景不挂载
        if (!"dataAgent".equals(request.getOutputStyle()) && subAgentRunner != null && subAgentRegistry != null) {
            // 子 Agent 派发工具只挂在主 Agent 上，避免 dataAgent 或子任务再次无限扩散执行边界。
            AgentDispatchTool agentDispatchTool =
                    new AgentDispatchTool(subAgentRunner, subAgentRegistry, activeAgentRunRegistry);
            addTool(toolCollection, agentDispatchTool, agentContext, AgentDispatchTool::setAgentContext);
        }

        // Task / Plan Mode 工具
        if (!"dataAgent".equals(request.getOutputStyle())) {
            registerPlanModeTools(toolCollection, agentContext);
        }
        return toolCollection;
    }

    /**
     * 装配 MCP 工具 + ToolSearch + Resources 元工具。
     * always/auto 延迟模式下仅 alwaysLoad 工具进入 tools[]，其余进 DeferredMcpCatalog。
     */
    private void attachMcpSurface(ToolCollection toolCollection, AgentContext agentContext, AgentRequest request) {
        List<McpToolInfo> discovered = List.of();
        try {
            // MCP 是动态外部能力，发现失败只降级远程工具；本地工具集合已经可以
            // 独立工作，不能因为远端配置异常而让 Agent 无法启动。
            discovered = mcpToolExecutor.discoverConfiguredTools();
        } catch (Exception e) {
            log.error("{} discover mcp tool failed", agentContext.getRequestId(), e);
        }

        DeferredMcpCatalog catalog = new DeferredMcpCatalog(discovered);
        agentContext.setDeferredMcpCatalog(catalog);

        boolean deferred = shouldDeferMcpTools(discovered.size());
        if (deferred) {
            for (McpToolInfo toolInfo : catalog.listActivated()) {
                toolCollection.addMcpTool(toolInfo);
            }
            if (!"dataAgent".equals(request.getOutputStyle())) {
                ToolSearchTool toolSearchTool = new ToolSearchTool();
                addTool(toolCollection, toolSearchTool, agentContext, ToolSearchTool::setAgentContext);
            }
            log.info("{} MCP deferred mode: catalog={}, preloaded={}",
                    agentContext.getRequestId(), catalog.size(), catalog.listActivated().size());
        } else {
            for (McpToolInfo toolInfo : discovered) {
                toolCollection.addMcpTool(toolInfo);
            }
        }

        if (!"dataAgent".equals(request.getOutputStyle())) {
            registerMcpResourceTools(toolCollection, agentContext);
        }
    }

    private void registerMcpResourceTools(ToolCollection toolCollection, AgentContext agentContext) {
        boolean hasResources = false;
        try {
            hasResources = mcpToolExecutor.hasAnyResources();
        } catch (Exception e) {
            log.debug("{} probe mcp resources failed: {}", agentContext.getRequestId(), e.getMessage());
        }
        // 始终注册 list/read：无资源时返回空列表，避免能力探测抖动导致工具集签名抖动。
        ListMcpResourcesTool listMcpResourcesTool = new ListMcpResourcesTool();
        addTool(toolCollection, listMcpResourcesTool, agentContext, ListMcpResourcesTool::setAgentContext);

        ReadMcpResourceTool readMcpResourceTool = new ReadMcpResourceTool();
        addTool(toolCollection, readMcpResourceTool, agentContext, ReadMcpResourceTool::setAgentContext);

        if (hasResources) {
            log.info("{} MCP resource tools registered (resources present)", agentContext.getRequestId());
        }
    }

    /**
     * always → 延迟；standard → 全量；auto → 超过阈值才延迟。
     */
    private boolean shouldDeferMcpTools(int discoveredCount) {
        String mode = reactorConfig.getMcpToolSearchMode();
        if (mode == null || mode.isBlank()) {
            mode = "always";
        }
        mode = mode.trim().toLowerCase();
        if ("standard".equals(mode) || "false".equals(mode) || "off".equals(mode)) {
            return false;
        }
        if ("auto".equals(mode)) {
            int threshold = reactorConfig.getMcpToolSearchAutoThreshold() == null
                    ? 8
                    : Math.max(1, reactorConfig.getMcpToolSearchAutoThreshold());
            return discoveredCount >= threshold;
        }
        // always / true / 默认
        return true;
    }

    private static List<String> parseToolNames(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    private void registerPlanModeTools(ToolCollection toolCollection, AgentContext agentContext) {
        // Plan Mode 工具共享当前上下文的 task/approval registry，工具调用结果才能
        // 在多轮执行中保持同一状态，而不是每次 build 重新创建孤立状态机。
        TaskCreateTool taskCreateTool = new TaskCreateTool();
        addTool(toolCollection, taskCreateTool, agentContext, TaskCreateTool::setAgentContext);

        TaskGetTool taskGetTool = new TaskGetTool();
        addTool(toolCollection, taskGetTool, agentContext, TaskGetTool::setAgentContext);

        TaskUpdateTool taskUpdateTool = new TaskUpdateTool();
        addTool(toolCollection, taskUpdateTool, agentContext, TaskUpdateTool::setAgentContext);

        TaskListTool taskListTool = new TaskListTool();
        addTool(toolCollection, taskListTool, agentContext, TaskListTool::setAgentContext);

        TodoWriteTool todoWriteTool = new TodoWriteTool();
        addTool(toolCollection, todoWriteTool, agentContext, TodoWriteTool::setAgentContext);

        TaskStopTool taskStopTool = new TaskStopTool();
        addTool(toolCollection, taskStopTool, agentContext, TaskStopTool::setAgentContext);

        TaskOutputTool taskOutputTool = new TaskOutputTool();
        addTool(toolCollection, taskOutputTool, agentContext, TaskOutputTool::setAgentContext);

        SendMessageTool sendMessageTool = new SendMessageTool();
        addTool(toolCollection, sendMessageTool, agentContext, SendMessageTool::setAgentContext);

        EnterPlanModeTool enterPlanModeTool = new EnterPlanModeTool(planArtifactStore);
        addTool(toolCollection, enterPlanModeTool, agentContext, EnterPlanModeTool::setAgentContext);

        ExitPlanModeTool exitPlanModeTool = new ExitPlanModeTool(planArtifactStore);
        addTool(toolCollection, exitPlanModeTool, agentContext, ExitPlanModeTool::setAgentContext);

        if (pendingUserQuestionRegistry != null) {
            AskUserQuestionTool askUserQuestionTool = new AskUserQuestionTool();
            addTool(toolCollection, askUserQuestionTool, agentContext, AskUserQuestionTool::setAgentContext);
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
        // workspace 工具统一绑定已解析的 cwd 和读状态存储；read/write/edit/list 等
        // 入口虽然分开，路径安全和会话级 read 状态仍由 WorkspaceService 共享。
        WorkspaceReadTool readTool = new WorkspaceReadTool(workspaceService, workspaceRuntimeOptions);
        addTool(toolCollection, readTool, agentContext, WorkspaceReadTool::setAgentContext);

        WorkspaceWriteTool writeTool = new WorkspaceWriteTool(workspaceService, workspaceRuntimeOptions);
        addTool(toolCollection, writeTool, agentContext, WorkspaceWriteTool::setAgentContext);

        WorkspaceEditTool editTool = new WorkspaceEditTool(workspaceService, workspaceRuntimeOptions);
        addTool(toolCollection, editTool, agentContext, WorkspaceEditTool::setAgentContext);

        WorkspaceListTool listTool = new WorkspaceListTool(workspaceService, workspaceRuntimeOptions);
        addTool(toolCollection, listTool, agentContext, WorkspaceListTool::setAgentContext);

        WorkspaceGlobTool globTool = new WorkspaceGlobTool(workspaceService, workspaceRuntimeOptions);
        addTool(toolCollection, globTool, agentContext, WorkspaceGlobTool::setAgentContext);

        WorkspaceGrepTool grepTool = new WorkspaceGrepTool(workspaceService, workspaceRuntimeOptions);
        addTool(toolCollection, grepTool, agentContext, WorkspaceGrepTool::setAgentContext);
    }

    private void registerDocReadTools(ToolCollection toolCollection, AgentContext agentContext) {
        CsvProcessorTool csvProcessorTool = new CsvProcessorTool();
        addTool(toolCollection, csvProcessorTool, agentContext, CsvProcessorTool::setAgentContext);

        ExcelReaderTool excelReaderTool = new ExcelReaderTool();
        addTool(toolCollection, excelReaderTool, agentContext, ExcelReaderTool::setAgentContext);

        HtmlProcessorTool htmlProcessorTool = new HtmlProcessorTool();
        addTool(toolCollection, htmlProcessorTool, agentContext, HtmlProcessorTool::setAgentContext);

        MarkdownProcessorTool markdownProcessorTool = new MarkdownProcessorTool();
        addTool(toolCollection, markdownProcessorTool, agentContext, MarkdownProcessorTool::setAgentContext);

        TextProcessorTool textProcessorTool = new TextProcessorTool();
        addTool(toolCollection, textProcessorTool, agentContext, TextProcessorTool::setAgentContext);

        WordReaderTool wordReaderTool = new WordReaderTool();
        addTool(toolCollection, wordReaderTool, agentContext, WordReaderTool::setAgentContext);

        PdfReaderTool pdfReaderTool = new PdfReaderTool();
        addTool(toolCollection, pdfReaderTool, agentContext, PdfReaderTool::setAgentContext);

        PdfStructureTool pdfStructureTool = new PdfStructureTool();
        addTool(toolCollection, pdfStructureTool, agentContext, PdfStructureTool::setAgentContext);

        CitationExtractorTool citationExtractorTool = new CitationExtractorTool();
        addTool(toolCollection, citationExtractorTool, agentContext, CitationExtractorTool::setAgentContext);

        ImageOcrTool imageOcrTool = new ImageOcrTool();
        addTool(toolCollection, imageOcrTool, agentContext, ImageOcrTool::setAgentContext);
    }

    private void registerDataPrepTools(ToolCollection toolCollection, AgentContext agentContext) {
        DataAggregateTool dataAggregateTool = new DataAggregateTool();
        addTool(toolCollection, dataAggregateTool, agentContext, DataAggregateTool::setAgentContext);

        DataCleanTool dataCleanTool = new DataCleanTool();
        addTool(toolCollection, dataCleanTool, agentContext, DataCleanTool::setAgentContext);

        DataMergeTool dataMergeTool = new DataMergeTool();
        addTool(toolCollection, dataMergeTool, agentContext, DataMergeTool::setAgentContext);

        DataTransformTool dataTransformTool = new DataTransformTool();
        addTool(toolCollection, dataTransformTool, agentContext, DataTransformTool::setAgentContext);

        DataValidateTool dataValidateTool = new DataValidateTool();
        addTool(toolCollection, dataValidateTool, agentContext, DataValidateTool::setAgentContext);

        SqlQueryTool sqlQueryTool = new SqlQueryTool();
        addTool(toolCollection, sqlQueryTool, agentContext, SqlQueryTool::setAgentContext);
    }

    /** 统一完成工具上下文绑定和注册，避免遗漏其中任一步骤。 */
    private <T extends BaseTool> void addTool(ToolCollection toolCollection,
                                              T tool,
                                              AgentContext agentContext,
                                              BiConsumer<T, AgentContext> contextBinder) {
        contextBinder.accept(tool, agentContext);
        toolCollection.addTool(tool);
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
        // skill 读写：workspace_* 虚拟路径 skills/ → runtime 库；执行：bash 沙箱物化 + skills/** sync-back
        SkillTool skillTool = new SkillTool(skillRegistry, skillRuntimeLayout);
        addTool(toolCollection, skillTool, agentContext, SkillTool::setAgentContext);

        // 可选遗留 skill_author（默认关闭；创作走 Skill Creator + workspace_*）
        if (skillRuntimeOptions.isAuthoringEnabled() && skillPackageService != null) {
            SkillAuthorTool authorTool = new SkillAuthorTool(skillPackageService, skillMaterializer);
            addTool(toolCollection, authorTool, agentContext, SkillAuthorTool::setAgentContext);
        }

        if (skillRuntimeOptions.isSandboxBashEnabled() && workspaceService.isEnabled()) {
            // 只发 HTTP 到 reactor-tool /v1/tool/bash；本机不执行
            BashTool bashTool = new BashTool(skillRuntimeOptions, skillVirtualPaths);
            addTool(toolCollection, bashTool, agentContext, BashTool::setAgentContext);
        }
    }

    /**
     * 会话级能力差集：禁用的 MCP 工具从本轮 ToolCollection 移除；
     * skill 禁用由 SkillTool + AgentContext.disabledSkillNames 在执行期拦截。
     */
    private void applySessionCapabilityFilter(ToolCollection toolCollection, AgentContext agentContext) {
        if (toolCollection == null || agentContext == null) {
            return;
        }
        Set<String> disabledMcps = agentContext.getDisabledMcpIds();
        if (disabledMcps == null || disabledMcps.isEmpty() || toolCollection.getMcpToolMap() == null) {
            return;
        }
        List<String> toRemove = new ArrayList<>();
        toolCollection.getMcpToolMap().forEach((name, info) -> {
            if (info == null) {
                return;
            }
            String mcpId = info.getMcpId() != null ? info.getMcpId() : info.getServerKey();
            if (mcpId != null && disabledMcps.contains(mcpId)) {
                toRemove.add(name);
            }
        });
        for (String name : toRemove) {
            toolCollection.getMcpToolMap().remove(name);
        }
        if (!toRemove.isEmpty()) {
            log.info("{} session capability filtered mcp tools: {}",
                    agentContext.getRequestId(), toRemove);
        }
    }

    private enum SkillAttachScope {
        REACT,
        PLAN_SOLVE
    }
}
