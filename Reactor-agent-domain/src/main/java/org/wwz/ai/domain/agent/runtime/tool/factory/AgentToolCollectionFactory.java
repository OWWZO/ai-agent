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
import org.wwz.ai.domain.agent.runtime.tool.common.skill.SkillTool;
import org.wwz.ai.domain.agent.runtime.tool.mcp.runtime.McpToolExecutor;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRegistry;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRuntimeOptions;
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
        // 工具集合是一次请求的能力快照：先绑定上下文和工作区，再按 outputStyle、
        // 配置白名单及 attachScope 装配工具，最后补 MCP、子 Agent 和 Plan Mode 能力。
        // 所有工具复用同一 AgentContext，才能保持 artifact、取消和 ledger 关联。
        ReactorRuntimeDependencies runtimeDependencies = requireRuntimeDependencies(agentContext);
        ensureWorkspaceRoot(agentContext);
        ToolCollection toolCollection = new ToolCollection();
        toolCollection.setAgentContext(agentContext);
        toolCollection.setMcpToolExecutor(runtimeDependencies.getOptionalMcpToolExecutor());

        // Hermes 风格长期记忆工具（有界策展）；依赖 LTM 装配时可用
        if (runtimeDependencies.getOptionalCuratedMemoryStore() != null
                || runtimeDependencies.getOptionalLtmManager() != null) {
            MemoryTool memoryTool = new MemoryTool();
            memoryTool.setAgentContext(agentContext);
            toolCollection.addTool(memoryTool);
        }
        if (runtimeDependencies.getOptionalSessionSearchService() != null) {
            SessionSearchTool sessionSearchTool = new SessionSearchTool();
            sessionSearchTool.setAgentContext(agentContext);
            toolCollection.addTool(sessionSearchTool);
        }

        // dataAgent 只暴露问数工具；普通 Agent 按配置装配 workspace、文档、数据处理、画布和外部检索工具。
        if ("dataAgent".equals(request.getOutputStyle())) {
            // dataAgent 只暴露问数入口，避免把普通 Agent 的文件/检索/编排工具带入
            // 数据查询协议；其它模式再按配置逐组挂载能力。
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
                            .getOrDefault("default", "search,web_fetch,web_search,code,code_execution,report,docgen,docread,dataprep,canvas,multimodalagent,image_generation,data_analysis")
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
                documentGenerateTool.setAgentContext(agentContext);
                toolCollection.addTool(documentGenerateTool);

                SlidesGenerateTool slidesGenerateTool = new SlidesGenerateTool();
                slidesGenerateTool.setAgentContext(agentContext);
                toolCollection.addTool(slidesGenerateTool);

                ExcelGeneratorTool excelGeneratorTool = new ExcelGeneratorTool();
                excelGeneratorTool.setAgentContext(agentContext);
                toolCollection.addTool(excelGeneratorTool);

                ChecklistGenerateTool checklistGenerateTool = new ChecklistGenerateTool();
                checklistGenerateTool.setAgentContext(agentContext);
                toolCollection.addTool(checklistGenerateTool);

                TemplateFillerTool templateFillerTool = new TemplateFillerTool();
                templateFillerTool.setAgentContext(agentContext);
                toolCollection.addTool(templateFillerTool);

                DocumentTemplateTool documentTemplateTool = new DocumentTemplateTool();
                documentTemplateTool.setAgentContext(agentContext);
                toolCollection.addTool(documentTemplateTool);

                ThemeDesignerTool themeDesignerTool = new ThemeDesignerTool();
                themeDesignerTool.setAgentContext(agentContext);
                toolCollection.addTool(themeDesignerTool);

                ChartGeneratorTool chartGeneratorTool = new ChartGeneratorTool();
                chartGeneratorTool.setAgentContext(agentContext);
                toolCollection.addTool(chartGeneratorTool);
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
                getHtmlCanvasGuideTool.setAgentContext(agentContext);
                toolCollection.addTool(getHtmlCanvasGuideTool);

                CanvasPublishTool canvasPublishTool = new CanvasPublishTool();
                canvasPublishTool.setAgentContext(agentContext);
                toolCollection.addTool(canvasPublishTool);

                GetGenuiGuideTool getGenuiGuideTool = new GetGenuiGuideTool();
                getGenuiGuideTool.setAgentContext(agentContext);
                toolCollection.addTool(getGenuiGuideTool);

                ListUiComponentsTool listUiComponentsTool = new ListUiComponentsTool();
                listUiComponentsTool.setAgentContext(agentContext);
                toolCollection.addTool(listUiComponentsTool);

                EmitUiTreeTool emitUiTreeTool = new EmitUiTreeTool();
                emitUiTreeTool.setAgentContext(agentContext);
                toolCollection.addTool(emitUiTreeTool);

                EmitUiPatchTool emitUiPatchTool = new EmitUiPatchTool();
                emitUiPatchTool.setAgentContext(agentContext);
                toolCollection.addTool(emitUiPatchTool);
            }
            if (agentToolList.contains("search")) {
                DeepSearchTool deepSearchTool = new DeepSearchTool();
                deepSearchTool.setAgentContext(agentContext);
                toolCollection.addTool(deepSearchTool);
            }
            if (agentToolList.contains("web_fetch") || agentToolList.contains("WebFetch")) {
                WebFetchTool webFetchTool = new WebFetchTool();
                webFetchTool.setAgentContext(agentContext);
                toolCollection.addTool(webFetchTool);
            }
            if (agentToolList.contains("web_search") || agentToolList.contains("WebSearch")) {
                WebSearchTool webSearchTool = new WebSearchTool();
                webSearchTool.setAgentContext(agentContext);
                toolCollection.addTool(webSearchTool);
            }
            if (agentToolList.contains("code_execution")) {
                CodeExecutionTool codeExecutionTool = new CodeExecutionTool();
                codeExecutionTool.setAgentContext(agentContext);
                toolCollection.addTool(codeExecutionTool);
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
            // MCP 是动态外部能力，发现失败只降级远程工具；本地工具集合已经可以
            // 独立工作，不能因为远端配置异常而让 Agent 无法启动。
            // MCP 工具属于动态配置，发现失败只影响远程工具，不阻断本地工具集合构建。
            for (McpToolInfo toolInfo : mcpToolExecutor.discoverConfiguredTools()) {
                toolCollection.addMcpTool(toolInfo);
            }
        } catch (Exception e) {
            log.error("{} add mcp tool failed", agentContext.getRequestId(), e);
        }

        // 主 Agent 可派发同步子 Agent；dataAgent 场景不挂载
        if (!"dataAgent".equals(request.getOutputStyle()) && subAgentRunner != null && subAgentRegistry != null) {
            // 子 Agent 派发工具只挂在主 Agent 上，避免 dataAgent 或子任务再次无限扩散执行边界。
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
        // Plan Mode 工具共享当前上下文的 task/approval registry，工具调用结果才能
        // 在多轮执行中保持同一状态，而不是每次 build 重新创建孤立状态机。
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
        // workspace 工具统一绑定已解析的 cwd 和读状态存储；read/write/edit/list 等
        // 入口虽然分开，路径安全和会话级 read 状态仍由 WorkspaceService 共享。
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

    private void registerDocReadTools(ToolCollection toolCollection, AgentContext agentContext) {
        CsvProcessorTool csvProcessorTool = new CsvProcessorTool();
        csvProcessorTool.setAgentContext(agentContext);
        toolCollection.addTool(csvProcessorTool);

        ExcelReaderTool excelReaderTool = new ExcelReaderTool();
        excelReaderTool.setAgentContext(agentContext);
        toolCollection.addTool(excelReaderTool);

        HtmlProcessorTool htmlProcessorTool = new HtmlProcessorTool();
        htmlProcessorTool.setAgentContext(agentContext);
        toolCollection.addTool(htmlProcessorTool);

        MarkdownProcessorTool markdownProcessorTool = new MarkdownProcessorTool();
        markdownProcessorTool.setAgentContext(agentContext);
        toolCollection.addTool(markdownProcessorTool);

        TextProcessorTool textProcessorTool = new TextProcessorTool();
        textProcessorTool.setAgentContext(agentContext);
        toolCollection.addTool(textProcessorTool);

        WordReaderTool wordReaderTool = new WordReaderTool();
        wordReaderTool.setAgentContext(agentContext);
        toolCollection.addTool(wordReaderTool);

        PdfReaderTool pdfReaderTool = new PdfReaderTool();
        pdfReaderTool.setAgentContext(agentContext);
        toolCollection.addTool(pdfReaderTool);

        PdfStructureTool pdfStructureTool = new PdfStructureTool();
        pdfStructureTool.setAgentContext(agentContext);
        toolCollection.addTool(pdfStructureTool);

        CitationExtractorTool citationExtractorTool = new CitationExtractorTool();
        citationExtractorTool.setAgentContext(agentContext);
        toolCollection.addTool(citationExtractorTool);

        ImageOcrTool imageOcrTool = new ImageOcrTool();
        imageOcrTool.setAgentContext(agentContext);
        toolCollection.addTool(imageOcrTool);
    }

    private void registerDataPrepTools(ToolCollection toolCollection, AgentContext agentContext) {
        DataAggregateTool dataAggregateTool = new DataAggregateTool();
        dataAggregateTool.setAgentContext(agentContext);
        toolCollection.addTool(dataAggregateTool);

        DataCleanTool dataCleanTool = new DataCleanTool();
        dataCleanTool.setAgentContext(agentContext);
        toolCollection.addTool(dataCleanTool);

        DataMergeTool dataMergeTool = new DataMergeTool();
        dataMergeTool.setAgentContext(agentContext);
        toolCollection.addTool(dataMergeTool);

        DataTransformTool dataTransformTool = new DataTransformTool();
        dataTransformTool.setAgentContext(agentContext);
        toolCollection.addTool(dataTransformTool);

        DataValidateTool dataValidateTool = new DataValidateTool();
        dataValidateTool.setAgentContext(agentContext);
        toolCollection.addTool(dataValidateTool);

        SqlQueryTool sqlQueryTool = new SqlQueryTool();
        sqlQueryTool.setAgentContext(agentContext);
        toolCollection.addTool(sqlQueryTool);
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
        // 脚本执行对齐 cc-haha：由 Bash / PowerShell 在 skill basePath 下运行，不再挂 script_runner_tool
        SkillTool skillTool = new SkillTool(skillRegistry);
        skillTool.setAgentContext(agentContext);
        toolCollection.addTool(skillTool);
    }

    private enum SkillAttachScope {
        REACT,
        PLAN_SOLVE
    }
}
