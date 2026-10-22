package org.wwz.ai.domain.agent.runtime.tool.factory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.mcp.model.McpToolInfo;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.cancel.ActiveAgentRunRegistry;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentRegistry;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentRunner;
import org.wwz.ai.domain.agent.runtime.tool.common.AgentDispatchTool;
import org.wwz.ai.domain.agent.runtime.askuser.PendingUserQuestionRegistry;
import org.wwz.ai.domain.agent.runtime.planmode.PendingPlanApprovalRegistry;
import org.wwz.ai.domain.agent.runtime.planmode.PlanArtifactStore;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.AskUserQuestionTool;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.RequestDesktopControlTool;
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
import org.wwz.ai.domain.agent.runtime.tool.common.ImageGenerationTool;
import org.wwz.ai.domain.agent.runtime.tool.common.docgen.ChartGeneratorTool;
import org.wwz.ai.domain.agent.runtime.tool.common.docgen.ChecklistGenerateTool;
import org.wwz.ai.domain.agent.runtime.tool.common.docgen.DocumentGenerateTool;
import org.wwz.ai.domain.agent.runtime.tool.common.docgen.DocumentTemplateTool;
import org.wwz.ai.domain.agent.runtime.tool.common.docgen.ExcelGeneratorTool;
import org.wwz.ai.domain.agent.runtime.tool.common.docgen.SlidesGenerateTool;
import org.wwz.ai.domain.agent.runtime.tool.common.docgen.TemplateFillerTool;
import org.wwz.ai.domain.agent.runtime.tool.common.openui.GetUiComponentsTool;
import org.wwz.ai.domain.agent.runtime.tool.common.openui.OpenUiToolNames;
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
import org.wwz.ai.domain.agent.runtime.tool.common.canvas.ListUiComponentsTool;
import org.wwz.ai.domain.agent.runtime.tool.common.MemoryTool;
import org.wwz.ai.domain.agent.runtime.tool.common.SessionSearchTool;
import org.wwz.ai.domain.agent.runtime.tool.cli.HostCliTool;
import org.wwz.ai.domain.agent.runtime.tool.codemode.CodeModeTool;
import org.wwz.ai.domain.agent.runtime.tool.browser.BrowserTool;
import org.wwz.ai.domain.agent.runtime.tool.browser.AgentBrowserTool;
import org.wwz.ai.domain.agent.runtime.tool.common.WebFetchTool;
import org.wwz.ai.domain.agent.runtime.tool.common.WebSearchTool;
import org.wwz.ai.domain.agent.runtime.tool.common.mcp.ListMcpResourcesTool;
import org.wwz.ai.domain.agent.runtime.tool.common.mcp.McpToolNames;
import org.wwz.ai.domain.agent.runtime.tool.common.mcp.ReadMcpResourceTool;
import org.wwz.ai.domain.agent.runtime.tool.common.mcp.ToolCallTool;
import org.wwz.ai.domain.agent.runtime.tool.common.mcp.ToolDescribeTool;
import org.wwz.ai.domain.agent.runtime.tool.common.mcp.ToolSearchTool;
import org.wwz.ai.domain.agent.runtime.tool.common.shell.BashTool;
import org.wwz.ai.domain.agent.runtime.tool.common.skill.SkillViewTool;
import org.wwz.ai.domain.agent.runtime.tool.common.skill.SkillsSearchTool;
import org.wwz.ai.domain.agent.runtime.tool.deferred.DeferredToolCatalog;
import org.wwz.ai.domain.agent.runtime.tool.deferred.DeferredToolEntry;
import org.wwz.ai.domain.agent.runtime.tool.deferred.LocalToolDeferralPolicy;
import org.wwz.ai.domain.agent.runtime.tool.mcp.port.McpToolExecutor;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillCatalog;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillLoader;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRegistry;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRuntimeLayout;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRuntimeOptions;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillScriptDiscoverer;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillVirtualPaths;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceGlobTool;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceGrepTool;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceListTool;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceReadTool;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceRuntimeOptions;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceService;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceEditTool;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceWriteTool;
import org.wwz.ai.domain.agent.cute.config.CuteConfig;
import org.wwz.ai.types.agent.config.CodeModeProperties;
import org.wwz.ai.domain.agent.runtime.command.AgentExecutionCommand;
import org.wwz.ai.domain.agent.runtime.CuteRuntimeDependencies;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * 统一构建 PlanSolve / ReAct 的工具集合，避免节点层重复拼装。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentToolCollectionFactory {

    private final CuteConfig cuteConfig;
    private final McpToolExecutor mcpToolExecutor;
    private final SkillRegistry skillRegistry;
    private final SkillLoader skillLoader;
    private final SkillScriptDiscoverer skillScriptDiscoverer;
    private final SkillRuntimeOptions skillRuntimeOptions;
    private final SkillRuntimeLayout skillRuntimeLayout;
    private final SkillVirtualPaths skillVirtualPaths;
    private final WorkspaceService workspaceService;
    private final WorkspaceRuntimeOptions workspaceRuntimeOptions;
    private final SubAgentRunner subAgentRunner;
    private final SubAgentRegistry subAgentRegistry;
    private final ActiveAgentRunRegistry activeAgentRunRegistry;
    private final PendingUserQuestionRegistry pendingUserQuestionRegistry;
    private final PendingPlanApprovalRegistry pendingPlanApprovalRegistry;
    private final PlanArtifactStore planArtifactStore;

    public ToolCollection buildForReact(AgentContext agentContext, AgentExecutionCommand request) {
        return build(agentContext, request, SkillAttachScope.REACT);
    }

    public ToolCollection buildForPlanSolve(AgentContext agentContext, AgentExecutionCommand request) {
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
        Set<String> allowed = new HashSet<>(parseToolNames(cuteConfig.getPlanSolveMainToolList()));
        if (allowed.contains(McpToolNames.TOOL_SEARCH)) {
            allowed.add(McpToolNames.TOOL_DESCRIBE);
            allowed.add(McpToolNames.TOOL_CALL);
        }
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
                        || (Boolean.TRUE.equals(tool.getAlwaysLoad()) && allowed.contains(McpToolNames.TOOL_SEARCH))) {
                    main.addMcpTool(tool);
                }
            });
        }
        if (fullToolCollection.getDeferredToolCatalog() != null) {
            boolean exposeCatalog = allowed.contains("*")
                    || allowed.contains(McpToolNames.TOOL_SEARCH);
            DeferredToolCatalog catalog = fullToolCollection.getDeferredToolCatalog().filter(entry ->
                    exposeCatalog || allowed.contains(entry.getName()));
            if (catalog.size() > 0) {
                main.setDeferredToolCatalog(catalog);
            }
        }
        main.setAgentContext(fullToolCollection.getAgentContext());
        main.restoreTaskScopedState(fullToolCollection.snapshotTaskScopedState());
        return main;
    }

    private ToolCollection build(AgentContext agentContext, AgentExecutionCommand request, SkillAttachScope attachScope) {
        // 工具集合是一次请求的能力快照：先绑定上下文和工作区，再按 outputStyle、
        // 配置白名单及 attachScope 装配工具，最后补 MCP、子 Agent 和 Plan Mode 能力。
        // 所有工具复用同一 AgentContext，才能保持 artifact、取消和 ledger 关联。
        CuteRuntimeDependencies runtimeDependencies = requireRuntimeDependencies(agentContext);
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
            // agent 通过 workspace_* 操作会话文件；内部产物上传不作为工具暴露给 LLM。
            if (workspaceService.isEnabled()) {
                registerWorkspaceTools(toolCollection, agentContext);
            }
            registerBrowserTool(toolCollection, agentContext);
            registerAgentBrowserTool(toolCollection, agentContext, request);
            registerHostCliTool(toolCollection, agentContext);
            registerCodeModeTool(toolCollection, agentContext);

            // 注意：`autobots.autoagent.tool_list` 未配置时用这里的默认串。application-*.yml 里的
            // `task.tool_list` 层级与 @Value 不一致（历史上一直没生效），所以新增能力要同时改这里，
            // 只改 yml 是无效的。
            List<String> agentToolList = parseToolNames(cuteConfig.getMultiAgentToolListMap()
                            .getOrDefault("default", "search,web_fetch,web_search,code_execution,docgen,docread,dataprep,canvas,image_generation,data_analysis,openui")
                    );

            if (matchesAny(agentToolList, "docgen", "document_generate", "slides_generate",
                    "excel_generator", "checklist_generate", "template_filler",
                    "document_template", "theme_designer", "chart_generator")) {
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
            if (matchesAny(agentToolList, "docread", "csv_processor", "excel_reader",
                    "pdf_reader", "word_reader", "html_processor", "markdown_processor",
                    "text_processor", "pdf_structure", "citation_extractor", "image_ocr")) {
                registerDocReadTools(toolCollection, agentContext);
            }
            if (matchesAny(agentToolList, "dataprep", "data_aggregate", "data_clean",
                    "data_merge", "data_transform", "data_validate", "sql_query")) {
                registerDataPrepTools(toolCollection, agentContext);
            }
            // canvas：把 workspace 里的 HTML 发布成可预览链接。它不产出界面 DSL，与 genui 树无关，保持可用。
            if (matchesAny(agentToolList, "canvas", "canvas_publish", "html_canvas")) {
                CanvasPublishTool canvasPublishTool = new CanvasPublishTool();
                addTool(toolCollection, canvasPublishTool, agentContext, CanvasPublishTool::setAgentContext);
            }
            // 旧 genui 树默认不装配（autobots.autoagent.genui.enabled=false）：
            // 界面通道已由 openui 承担，两套 DSL 同时在场会让模型在 emit_ui_tree 和 openui-lang 之间摇摆。
            // 工具代码保留，置 true 可回滚。
            if (cuteConfig.isGenuiEnabled()
                    && matchesAny(agentToolList, "canvas", "genui")) {
                GetGenuiGuideTool getGenuiGuideTool = new GetGenuiGuideTool();
                addTool(toolCollection, getGenuiGuideTool, agentContext, GetGenuiGuideTool::setAgentContext);

                ListUiComponentsTool listUiComponentsTool = new ListUiComponentsTool();
                addTool(toolCollection, listUiComponentsTool, agentContext, ListUiComponentsTool::setAgentContext);

                EmitUiTreeTool emitUiTreeTool = new EmitUiTreeTool();
                addTool(toolCollection, emitUiTreeTool, agentContext, EmitUiTreeTool::setAgentContext);

                EmitUiPatchTool emitUiPatchTool = new EmitUiPatchTool();
                addTool(toolCollection, emitUiPatchTool, agentContext, EmitUiPatchTool::setAgentContext);
            }
            // openui-lang 扩展组件目录（与上面的 genui canvas 链路相互独立）。
            if (matchesAny(agentToolList, "openui", OpenUiToolNames.GET_UI_COMPONENTS)) {
                GetUiComponentsTool getUiComponentsTool = new GetUiComponentsTool();
                addTool(toolCollection, getUiComponentsTool, agentContext, GetUiComponentsTool::setAgentContext);
            }
            if (agentToolList.contains("search")) {
                DeepSearchTool deepSearchTool = new DeepSearchTool();
                addTool(toolCollection, deepSearchTool, agentContext, DeepSearchTool::setAgentContext);
            }
            if (matchesAny(agentToolList, "web_fetch", "WebFetch")) {
                WebFetchTool webFetchTool = new WebFetchTool();
                addTool(toolCollection, webFetchTool, agentContext, WebFetchTool::setAgentContext);
            }
            if (matchesAny(agentToolList, "web_search", "WebSearch")) {
                WebSearchTool webSearchTool = new WebSearchTool();
                addTool(toolCollection, webSearchTool, agentContext, WebSearchTool::setAgentContext);
            }
            if (agentToolList.contains("code_execution")) {
                CodeExecutionTool codeExecutionTool = new CodeExecutionTool();
                addTool(toolCollection, codeExecutionTool, agentContext, CodeExecutionTool::setAgentContext);
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
     * 装配本地/MCP 工具 + 三件套 bridge + Resources 元工具。
     * 本地和 MCP 候选在同一个不可变 catalog 中，不会因为搜索而进入 tools[]。
     */
    private void attachMcpSurface(ToolCollection toolCollection, AgentContext agentContext, AgentExecutionCommand request) {
        List<McpToolInfo> discovered = List.of();
        try {
            // MCP 是动态外部能力，发现失败只降级远程工具；本地工具集合已经可以
            // 独立工作，不能因为远端配置异常而让 Agent 无法启动。
            discovered = mcpToolExecutor.discoverConfiguredTools();
        } catch (Exception e) {
            log.error("{} discover mcp tool failed", agentContext.getRequestId(), e);
        }

        List<McpToolInfo> visible = new ArrayList<>();
        List<McpToolInfo> deferredTools = new ArrayList<>();
        for (McpToolInfo toolInfo : discovered) {
            if (toolInfo == null) {
                continue;
            }
            if (isDisabledMcp(agentContext, toolInfo)) {
                continue;
            }
            if (Boolean.TRUE.equals(toolInfo.getAlwaysLoad())) {
                visible.add(toolInfo);
            } else {
                deferredTools.add(toolInfo);
            }
        }

        int localDeferredCandidates = countLocalDeferredCandidates(toolCollection);
        boolean shouldDefer = shouldDeferTools(localDeferredCandidates + deferredTools.size());
        if (shouldDefer) {
            List<DeferredToolEntry> catalogEntries = moveLocalDeferredTools(toolCollection);
            for (McpToolInfo toolInfo : visible) {
                toolCollection.addMcpTool(toolInfo);
            }
            for (McpToolInfo toolInfo : deferredTools) {
                catalogEntries.add(DeferredToolCatalog.mcpEntry(toolInfo));
            }
            DeferredToolCatalog catalog = new DeferredToolCatalog(catalogEntries);
            if (catalog.size() > 0) {
                toolCollection.setDeferredToolCatalog(catalog);
                addTool(toolCollection, new ToolSearchTool(), agentContext, ToolSearchTool::setAgentContext);
                addTool(toolCollection, new ToolDescribeTool(), agentContext, ToolDescribeTool::setAgentContext);
                addTool(toolCollection, new ToolCallTool(), agentContext, ToolCallTool::setAgentContext);
            }
            log.info("{} MCP deferred mode: catalog={}, alwaysLoad={}",
                    agentContext.getRequestId(), catalog.size(), visible.size());
        } else {
            for (McpToolInfo toolInfo : visible) {
                toolCollection.addMcpTool(toolInfo);
            }
            for (McpToolInfo toolInfo : deferredTools) {
                toolCollection.addMcpTool(toolInfo);
            }
        }

        boolean dataAgent = request != null && "dataAgent".equals(request.getOutputStyle());
        if (!dataAgent) {
            registerMcpResourceTools(toolCollection, agentContext);
        }
    }

    private static boolean isDisabledMcp(AgentContext agentContext, McpToolInfo toolInfo) {
        if (agentContext == null || agentContext.getDisabledMcpIds() == null
                || agentContext.getDisabledMcpIds().isEmpty()) {
            return false;
        }
        String mcpId = toolInfo.getMcpId() != null ? toolInfo.getMcpId() : toolInfo.getServerKey();
        return mcpId != null && agentContext.getDisabledMcpIds().contains(mcpId);
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
     * always → 有 deferred 才延迟；standard/off → 全量；auto → deferred 数超过阈值才延迟。
     */
    private boolean shouldDeferTools(int deferredCount) {
        String mode = cuteConfig.getMcpToolSearchMode();
        if (mode == null || mode.isBlank()) {
            mode = "always";
        }
        mode = mode.trim().toLowerCase();
        if ("standard".equals(mode) || "false".equals(mode) || "off".equals(mode)) {
            return false;
        }
        if (deferredCount <= 0) {
            return false;
        }
        if ("auto".equals(mode)) {
            int threshold = cuteConfig.getMcpToolSearchAutoThreshold() == null
                    ? 8
                    : Math.max(1, cuteConfig.getMcpToolSearchAutoThreshold());
            return deferredCount >= threshold;
        }
        // always / true / 默认
        return true;
    }

    private static int countLocalDeferredCandidates(ToolCollection toolCollection) {
        if (toolCollection == null || toolCollection.getToolMap() == null) {
            return 0;
        }
        int count = 0;
        for (String name : toolCollection.getToolMap().keySet()) {
            if (LocalToolDeferralPolicy.isCandidate(name)) {
                count++;
            }
        }
        return count;
    }

    private static List<DeferredToolEntry> moveLocalDeferredTools(ToolCollection toolCollection) {
        List<DeferredToolEntry> entries = new ArrayList<>();
        if (toolCollection == null || toolCollection.getToolMap() == null) {
            return entries;
        }
        List<String> names = new ArrayList<>();
        for (Map.Entry<String, BaseTool> entry : toolCollection.getToolMap().entrySet()) {
            if (LocalToolDeferralPolicy.isCandidate(entry.getKey())) {
                entries.add(DeferredToolCatalog.localEntry(entry.getValue(), "cute"));
                names.add(entry.getKey());
            }
        }
        for (String name : names) {
            toolCollection.getToolMap().remove(name);
        }
        return entries;
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

        SendMessageTool sendMessageTool = new SendMessageTool(subAgentRunner, activeAgentRunRegistry);
        addTool(toolCollection, sendMessageTool, agentContext, SendMessageTool::setAgentContext);

        EnterPlanModeTool enterPlanModeTool = new EnterPlanModeTool(planArtifactStore);
        addTool(toolCollection, enterPlanModeTool, agentContext, EnterPlanModeTool::setAgentContext);

        ExitPlanModeTool exitPlanModeTool = new ExitPlanModeTool(planArtifactStore);
        addTool(toolCollection, exitPlanModeTool, agentContext, ExitPlanModeTool::setAgentContext);

        if (pendingUserQuestionRegistry != null) {
            AskUserQuestionTool askUserQuestionTool = new AskUserQuestionTool();
            addTool(toolCollection, askUserQuestionTool, agentContext, AskUserQuestionTool::setAgentContext);
            RequestDesktopControlTool requestDesktopControlTool = new RequestDesktopControlTool();
            addTool(toolCollection, requestDesktopControlTool, agentContext, RequestDesktopControlTool::setAgentContext);
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

    /**
     * browser（用户本机 relay）常驻挂载。
     * <p>
     * 注册只看部署级能力（opencli 启用 + CLI 可解析），<b>不看用户 relay 是否在线</b>：
     * 连接状态是运行期事实，用它决定 tools[] 会让工具集合随用户开关浏览器而抖动，
     * 既打爆 prompt cache，也让模型在"这一轮没有浏览器能力"的假象下放弃操作。
     * relay 离线由 {@code BrowserTool} 在执行期返回可读失败，模型据此提示用户连接后重试。
     * </p>
     */
    private void registerBrowserTool(ToolCollection toolCollection, AgentContext agentContext) {
        if (agentContext == null || agentContext.getRuntimeDependencies() == null) {
            return;
        }
        CuteRuntimeDependencies deps = agentContext.getRuntimeDependencies();
        var properties = deps.getOpenCliProperties();
        var cli = deps.getOptionalCliExecutionPort();
        if (properties == null || !properties.isEnabled() || cli == null || !cli.isResolvable(properties.getCommand())) {
            return;
        }
        addTool(toolCollection, new BrowserTool(), agentContext, BrowserTool::setAgentContext);
    }

    private void registerAgentBrowserTool(ToolCollection toolCollection,
                                          AgentContext agentContext,
                                          AgentExecutionCommand request) {
        if (request == null || "dataAgent".equals(request.getOutputStyle())
                || agentContext == null || agentContext.getRuntimeDependencies() == null) {
            return;
        }
        CuteRuntimeDependencies deps = agentContext.getRuntimeDependencies();
        var properties = deps.getOpenCliProperties();
        var cli = deps.getOptionalCliExecutionPort();
        var port = deps.getOptionalAgentBrowserSessionPort();
        if (properties == null || !properties.isEnabled() || cli == null
                || !cli.isResolvable(properties.getCommand()) || port == null || !port.isConfigured()) {
            return;
        }
        addTool(toolCollection, new AgentBrowserTool(), agentContext, AgentBrowserTool::setAgentContext);
    }

    private void registerHostCliTool(ToolCollection toolCollection, AgentContext agentContext) {
        if (agentContext == null || agentContext.getRuntimeDependencies() == null) {
            return;
        }
        CuteRuntimeDependencies deps = agentContext.getRuntimeDependencies();
        var properties = deps.getHostCliProperties();
        var cli = deps.getOptionalCliExecutionPort();
        if (properties == null || !properties.isEnabled() || cli == null
                || properties.getAllow() == null || properties.getAllow().isEmpty()) {
            return;
        }
        List<String> available = new ArrayList<>();
        for (String tool : properties.getAllow()) {
            if (StringUtils.isNotBlank(tool) && cli.isResolvable(tool.trim())) {
                available.add(tool.trim());
            }
        }
        if (available.isEmpty()) {
            return;
        }
        addTool(toolCollection, new HostCliTool(available), agentContext, HostCliTool::setAgentContext);
    }

    /**
     * Code Mode（GraalJS 同 JVM JS 编排）。
     * <p>
     * 注册条件只看三件事：配置启用、执行器已装配、workspace 可用；
     * <b>不因为 Agent Browser 是否可用来决定</b>，也不影响现有 browser / host_cli 注册。
     * </p>
     */
    private void registerCodeModeTool(ToolCollection toolCollection, AgentContext agentContext) {
        if (agentContext == null || agentContext.getRuntimeDependencies() == null) {
            return;
        }
        CuteRuntimeDependencies deps = agentContext.getRuntimeDependencies();
        CodeModeProperties properties = deps.getOptionalCodeModeProperties();
        if (properties == null || !properties.isEnabled()) {
            return;
        }
        if (deps.getOptionalCodeModeExecutorPort() == null || !workspaceService.isEnabled()) {
            return;
        }
        CodeModeTool codeModeTool = new CodeModeTool(properties);
        codeModeTool.setExecutorPort(deps.getOptionalCodeModeExecutorPort());
        addTool(toolCollection, codeModeTool, agentContext, CodeModeTool::setAgentContext);
    }

    /** 统一完成工具上下文绑定和注册，避免遗漏其中任一步骤。 */
    private <T extends BaseTool> void addTool(ToolCollection toolCollection,
                                              T tool,
                                              AgentContext agentContext,
                                              BiConsumer<T, AgentContext> contextBinder) {
        contextBinder.accept(tool, agentContext);
        toolCollection.addTool(tool);
    }

    /**
     * 白名单组判定：组名或组内任一工具别名命中即装配整组。
     * <p>
     * 组名与别名是<b>或</b>关系——配置里写组名（如 {@code docread}）或写某个具体
     * 工具别名（如 {@code pdf_reader}）都应触发整组装配，因为组内工具彼此协作，
     * 只装一个会让模型拿到半套能力。
     */
    private static boolean matchesAny(List<String> agentToolList, String group, String... aliases) {
        if (agentToolList.contains(group)) {
            return true;
        }
        for (String alias : aliases) {
            if (agentToolList.contains(alias)) {
                return true;
            }
        }
        return false;
    }

    private CuteRuntimeDependencies requireRuntimeDependencies(AgentContext agentContext) {
        if (agentContext == null || agentContext.getRuntimeDependencies() == null) {
            throw new IllegalStateException("AgentToolCollectionFactory 缺少 CuteRuntimeDependencies");
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
        if (!(skillRegistry instanceof SkillCatalog catalog)) {
            return;
        }
        SkillViewTool skillViewTool = new SkillViewTool(
                catalog,
                skillLoader,
                skillRuntimeLayout,
                skillScriptDiscoverer);
        addTool(toolCollection, skillViewTool, agentContext, SkillViewTool::setAgentContext);
        SkillsSearchTool skillsSearchTool = new SkillsSearchTool(catalog);
        addTool(toolCollection, skillsSearchTool, agentContext, SkillsSearchTool::setAgentContext);

        if (skillRuntimeOptions.isSandboxBashEnabled() && workspaceService.isEnabled()) {
            // 只发 HTTP 到 cute-tool /v1/tool/bash；本机不执行
            BashTool bashTool = new BashTool(skillRuntimeOptions, skillVirtualPaths);
            addTool(toolCollection, bashTool, agentContext, BashTool::setAgentContext);
        }
    }

    /**
     * 会话级能力差集：禁用的 MCP 工具从本轮 ToolCollection 移除；
     * skill 禁用由 SkillViewTool + AgentContext.disabledSkillNames 在执行期拦截。
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
        if (toolCollection.getDeferredToolCatalog() != null) {
            DeferredToolCatalog filtered = toolCollection.getDeferredToolCatalog().filter(entry ->
                    entry.isLocal() || !isDisabledMcp(agentContext, entry.getMcpToolInfo()));
            toolCollection.setDeferredToolCatalog(filtered.size() == 0 ? null : filtered);
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
