package org.wwz.ai.domain.agent.runtime.tool;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.tool.common.AgentDispatchTool;
import org.wwz.ai.domain.agent.runtime.tool.common.MemoryTool;
import org.wwz.ai.domain.agent.runtime.tool.common.SessionSearchTool;
import org.wwz.ai.domain.agent.runtime.tool.common.WebFetchTool;
import org.wwz.ai.domain.agent.runtime.tool.common.WebSearchTool;
import org.wwz.ai.domain.agent.runtime.tool.common.canvas.CanvasToolNames;
import org.wwz.ai.domain.agent.runtime.tool.common.mcp.McpToolNames;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.AskUserQuestionTool;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.RequestDesktopControlTool;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.TaskToolNames;
import org.wwz.ai.domain.agent.runtime.tool.common.shell.BashTool;
import org.wwz.ai.domain.agent.runtime.tool.common.skill.SkillViewTool;
import org.wwz.ai.domain.agent.runtime.tool.common.skill.SkillsSearchTool;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceReadTool;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 工具结果投影：把结构化 llmData 压成主智能体可见的紧凑文本。
 * <p>
 * 对齐 zCode 的 {@code ToolEntry.formatModelContent}：模型只拿它需要的，UI / 账本仍拿完整 JSON。
 * 改造后三通道收敛为两列：
 * <ul>
 *   <li>{@code llm_oberserve} ← 本类产出（模型实际看到的文本，与 working_memory 一致）</li>
 *   <li>{@code tool_result} ← {@code llmData} 的完整 JSON（历史回放 / 前端卡片）</li>
 * </ul>
 * 未注册的工具、失败结果、ok=false、缺键、异常一律返回 {@code null}，由调用方回落 JSON 序列化。
 */
@Slf4j
public final class ToolObservationFormatter {

    private static final Map<String, Function<Map<?, ?>, String>> PROJECTORS = Map.ofEntries(
            Map.entry("workspace_read", ToolObservationFormatter::projectRead),
            Map.entry("workspace_grep", ToolObservationFormatter::projectGrep),
            Map.entry("workspace_glob", ToolObservationFormatter::projectGlob),
            Map.entry("workspace_list", ToolObservationFormatter::projectList),
            Map.entry("workspace_edit", ToolObservationFormatter::projectEdit),
            Map.entry("workspace_write", ToolObservationFormatter::projectWrite),
            Map.entry(TaskToolNames.TASK_OUTPUT, ToolObservationFormatter::projectTaskOutput),
            Map.entry(SessionSearchTool.TOOL_NAME, ToolObservationFormatter::projectSessionSearch),
            Map.entry("host_cli", ToolObservationFormatter::projectCli),
            Map.entry("browser", ToolObservationFormatter::projectCli),
            Map.entry("agent_browser", ToolObservationFormatter::projectCli),
            // 检索类：命中列表是候选集，snippet 截断后仍是模型判断相关性的依据。
            Map.entry(WebSearchTool.TOOL_NAME, ToolObservationFormatter::projectWebSearch),
            Map.entry(WebFetchTool.TOOL_NAME, ToolObservationFormatter::projectWebFetch),
            Map.entry("deep_search", ToolObservationFormatter::projectDeepSearch),
            Map.entry(SkillViewTool.NAME, ToolObservationFormatter::projectSkillView),
            Map.entry(SkillsSearchTool.NAME, ToolObservationFormatter::projectSkillsSearch),
            // 子 Agent：agentId 必须保留（SendMessage 靠它续跑），其余元数据丢弃。
            Map.entry(AgentDispatchTool.NAME, ToolObservationFormatter::projectAgent),
            Map.entry(TaskToolNames.SEND_MESSAGE, ToolObservationFormatter::projectSendMessage),
            Map.entry(TaskToolNames.TASK_STOP, ToolObservationFormatter::projectTaskStop),
            Map.entry(TaskToolNames.TASK_CREATE, ToolObservationFormatter::projectTaskRecord),
            Map.entry(TaskToolNames.TASK_UPDATE, ToolObservationFormatter::projectTaskRecord),
            Map.entry(TaskToolNames.TASK_GET, ToolObservationFormatter::projectTaskRecord),
            Map.entry(TaskToolNames.TASK_LIST, ToolObservationFormatter::projectTaskList),
            Map.entry(TaskToolNames.TODO_WRITE, ToolObservationFormatter::projectTaskList),
            Map.entry(TaskToolNames.ENTER_PLAN_MODE, ToolObservationFormatter::projectEnterPlanMode),
            Map.entry(TaskToolNames.EXIT_PLAN_MODE, ToolObservationFormatter::projectExitPlanMode),
            Map.entry(AskUserQuestionTool.NAME, ToolObservationFormatter::projectAskUserQuestion),
            Map.entry(RequestDesktopControlTool.NAME, ToolObservationFormatter::projectDesktopControl),
            Map.entry(CanvasToolNames.EMIT_UI_TREE, ToolObservationFormatter::projectEmitUiTree),
            Map.entry(CanvasToolNames.EMIT_UI_PATCH, ToolObservationFormatter::projectEmitUiPatch),
            Map.entry(CanvasToolNames.CANVAS_PUBLISH, ToolObservationFormatter::projectCanvasPublish),
            Map.entry(McpToolNames.TOOL_SEARCH, ToolObservationFormatter::projectToolSearch),
            Map.entry(McpToolNames.TOOL_DESCRIBE, ToolObservationFormatter::projectToolDescribe),
            Map.entry(MemoryTool.TOOL_NAME, ToolObservationFormatter::projectMemory),
            Map.entry("code_execution", ToolObservationFormatter::projectCodeExecution),
            Map.entry("bash", ToolObservationFormatter::projectBash)
    );

    private ToolObservationFormatter() {
    }

    /**
     * 命中返回紧凑文本；未注册、ok=false、缺键或异常一律返回 {@code null}。
     * <p>
     * {@code llmData} 不一定是 Map：deep_search 等工具传的是 POJO，这里统一归一化后再投影。
     */
    public static String format(String toolName, Object llmData) {
        if (toolName == null || llmData == null) {
            return null;
        }
        Function<Map<?, ?>, String> projector = PROJECTORS.get(toolName);
        if (projector == null) {
            return null;
        }
        Map<?, ?> data = asMap(llmData);
        if (data == null) {
            return null;
        }
        // softFail 的 ok=false 承载真语义（业务否决 / 未找到），保留 JSON 让模型看清结构。
        if (Boolean.FALSE.equals(data.get("ok"))) {
            return null;
        }
        try {
            return projector.apply(data);
        } catch (Exception e) {
            log.debug("tool observation projection skipped, tool={}", toolName, e);
            return null;
        }
    }

    /**
     * 把 POJO / String 归一化成字段映射；无法归一化时返回 {@code null}，调用方回落 JSON。
     */
    private static Map<?, ?> asMap(Object llmData) {
        if (llmData instanceof Map<?, ?> data) {
            return data;
        }
        if (llmData instanceof String || llmData instanceof Number || llmData instanceof Boolean) {
            return null;
        }
        try {
            Object parsed = JSON.toJSON(llmData);
            return parsed instanceof Map<?, ?> data ? data : null;
        } catch (Exception e) {
            log.debug("tool observation llmData is not map-like, class={}", llmData.getClass().getName(), e);
            return null;
        }
    }

    // ---- workspace_read ----

    private static String projectRead(Map<?, ?> data) {
        String type = text(data.get("type"));
        if ("image".equals(type)) {
            // 元数据本身很短，且模型必须知道这是图片（正文走多模态 media），保留 JSON。
            return null;
        }
        if ("file_unchanged".equals(type)) {
            String stub = text(data.get("message"));
            return StringUtils.isBlank(stub) ? WorkspaceReadTool.FILE_UNCHANGED_STUB : stub;
        }
        String content = text(data.get("content"));
        if (StringUtils.isBlank(content)) {
            return "(file is empty)";
        }
        StringBuilder out = new StringBuilder(content);
        int startLine = intOf(data.get("startLine"), 0);
        int endLine = intOf(data.get("endLine"), 0);
        int totalLines = intOf(data.get("totalLines"), 0);
        if (totalLines > endLine) {
            out.append("\n[file has ").append(totalLines).append(" lines; shown ")
                    .append(startLine).append('-').append(endLine).append(']');
        }
        if (Boolean.TRUE.equals(data.get("truncated"))) {
            out.append("\n[truncated at line ").append(endLine)
                    .append("; call workspace_read with start_line=").append(endLine + 1)
                    .append(" to continue]");
        }
        return out.toString();
    }

    // ---- workspace_grep ----

    private static String projectGrep(Map<?, ?> data) {
        List<?> matches = list(data.get("matches"));
        if (matches.isEmpty()) {
            return "No matches found";
        }
        StringBuilder out = new StringBuilder();
        for (Object item : matches) {
            if (!(item instanceof Map<?, ?> row)) {
                return null;
            }
            String path = text(row.get("path"));
            String lineText = text(row.get("text"));
            if (StringUtils.isBlank(path)) {
                return null;
            }
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(path).append(':').append(intOf(row.get("line"), 0)).append(':').append(lineText);
        }
        if (Boolean.TRUE.equals(data.get("truncated"))) {
            out.append("\n[truncated; narrow the pattern or path]");
        }
        return out.toString();
    }

    // ---- workspace_glob ----

    private static String projectGlob(Map<?, ?> data) {
        List<?> files = list(data.get("files"));
        if (files.isEmpty()) {
            return "No files matched";
        }
        StringBuilder out = new StringBuilder();
        for (Object file : files) {
            String path = text(file);
            if (StringUtils.isBlank(path)) {
                return null;
            }
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(path);
        }
        if (Boolean.TRUE.equals(data.get("truncated"))) {
            out.append("\n[truncated; use a more specific pattern]");
        }
        return out.toString();
    }

    // ---- workspace_list ----

    private static String projectList(Map<?, ?> data) {
        List<?> entries = list(data.get("entries"));
        if (entries.isEmpty()) {
            return "(empty directory)";
        }
        StringBuilder out = new StringBuilder();
        for (Object item : entries) {
            if (!(item instanceof Map<?, ?> row)) {
                return null;
            }
            String path = text(row.get("path"));
            if (StringUtils.isBlank(path)) {
                return null;
            }
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append("DIR".equals(text(row.get("type"))) ? "DIR  " : "FILE ").append(path);
        }
        if (Boolean.TRUE.equals(data.get("truncated"))) {
            out.append("\n[truncated; list a subdirectory or reduce max_depth]");
        }
        return out.toString();
    }

    // ---- workspace_edit ----

    private static String projectEdit(Map<?, ?> data) {
        String path = text(data.get("path"));
        if (StringUtils.isBlank(path)) {
            return null;
        }
        int replacements = intOf(data.get("replacements"), 1);
        StringBuilder out = new StringBuilder()
                .append("The file ").append(path).append(" has been updated successfully. (")
                .append(replacements).append(replacements == 1 ? " replacement)" : " replacements)");
        // diff 是模型确认改动内容的唯一依据，必须原样保留。
        String diff = text(data.get("diff"));
        if (StringUtils.isNotBlank(diff)) {
            out.append("\n\n").append(diff);
        }
        return out.toString();
    }

    // ---- workspace_write ----

    private static String projectWrite(Map<?, ?> data) {
        String path = text(data.get("path"));
        if (StringUtils.isBlank(path)) {
            return null;
        }
        return "File written successfully: " + path;
    }

    // ---- TaskOutput ----

    private static String projectTaskOutput(Map<?, ?> data) {
        StringBuilder out = new StringBuilder(text(data.get("message")));
        // retrieval_status 必须保留：模型靠它区分「任务仍在跑」和「已完成但没输出」。
        // 丢掉它，block=false 时模型会把未终态当成终态，后台子 Agent 的结果被静默丢弃。
        String retrieval = text(data.get("retrieval_status"));
        if (StringUtils.isNotBlank(retrieval)) {
            out.append("\nretrieval_status: ").append(retrieval);
        }
        String output = text(data.get("output"));
        if (StringUtils.isNotBlank(output)) {
            out.append("\n\n").append(output);
        }
        String error = text(data.get("error"));
        if (StringUtils.isNotBlank(error)) {
            out.append("\n\nError: ").append(error);
        }
        return out.length() == 0 ? null : out.toString();
    }

    // ---- session_search ----

    private static String projectSessionSearch(Map<?, ?> data) {
        // 入参是模型刚发过的，回显纯属浪费；只给检索结果。
        Object result = data.get("result");
        if (result == null) {
            return null;
        }
        String text = result instanceof String str ? str : JSON.toJSONString(result);
        return StringUtils.isBlank(text) ? null : text;
    }

    // ---- WebSearch ----

    /** 单条命中摘要的截断长度：200 会把一句话结论截断，300 能保住大多数摘要的主干。 */
    static final int SNIPPET_MAX_CHARS = 300;

    private static String projectWebSearch(Map<?, ?> data) {
        List<?> hits = list(data.get("hits"));
        StringBuilder out = new StringBuilder();
        String summary = text(data.get("summary"));
        if (StringUtils.isNotBlank(summary)) {
            out.append("Summary:\n").append(summary.strip()).append("\n\n");
        }
        if (hits.isEmpty()) {
            out.append("No results found");
        } else {
            int index = 1;
            for (Object item : hits) {
                if (!(item instanceof Map<?, ?> row)) {
                    return null;
                }
                String url = text(row.get("url"));
                if (StringUtils.isBlank(url)) {
                    return null;
                }
                if (out.length() > 0) {
                    out.append('\n');
                }
                out.append('[').append(index++).append("] ").append(StringUtils.defaultString(text(row.get("title"))))
                        .append('\n').append("    ").append(url);
                String snippet = text(row.get("snippet"));
                if (StringUtils.isNotBlank(snippet)) {
                    out.append('\n').append("    ").append(truncate(snippet.strip(), SNIPPET_MAX_CHARS));
                }
            }
        }
        // 引用要求本身就是固定提示，不必作为独立字段重复占位。
        out.append("\n\nCite sources with markdown hyperlinks.");
        return out.toString();
    }

    // ---- WebFetch ----

    private static String projectWebFetch(Map<?, ?> data) {
        String content = text(data.get("content"));
        if (StringUtils.isBlank(content)) {
            return null;
        }
        StringBuilder out = new StringBuilder(content.stripTrailing());
        if (Boolean.TRUE.equals(data.get("degraded"))) {
            out.append("\n[model extract failed; returned truncated page text]");
        }
        String extractError = text(data.get("extractError"));
        if (StringUtils.isNotBlank(extractError)) {
            out.append("\nextract_error: ").append(extractError);
        }
        // 2xx 是常态，只有非成功状态码才值得占位。
        int status = intOf(data.get("status"), 0);
        if (status >= 400) {
            out.append("\nstatus: ").append(status);
        }
        return out.toString();
    }

    // ---- deep_search ----

    private static String projectDeepSearch(Map<?, ?> data) {
        StringBuilder out = new StringBuilder();
        String answer = text(data.get("answerSummary"));
        if (StringUtils.isNotBlank(answer)) {
            out.append(answer.strip());
        }
        appendChapters(out, data.get("chapters"));
        appendSearchResults(out, data.get("results"));
        return out.length() == 0 ? null : out.toString();
    }

    private static void appendChapters(StringBuilder out, Object chapters) {
        if (!(chapters instanceof List<?> items) || items.isEmpty()) {
            return;
        }
        out.append("\n\n## Chapters");
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> chapter)) {
                continue;
            }
            out.append("\n### ").append(StringUtils.defaultString(text(chapter.get("title"))));
            String summary = text(chapter.get("summary"));
            if (StringUtils.isNotBlank(summary)) {
                out.append('\n').append(summary.strip());
            }
        }
    }

    private static void appendSearchResults(StringBuilder out, Object results) {
        if (!(results instanceof List<?> items) || items.isEmpty()) {
            return;
        }
        out.append("\n\n## Sources");
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> row)) {
                continue;
            }
            String query = text(row.get("query"));
            out.append("\n- ").append(StringUtils.defaultString(query));
            if (row.get("docs") instanceof List<?> docs) {
                for (Object doc : docs) {
                    if (!(doc instanceof Map<?, ?> d)) {
                        continue;
                    }
                    String url = text(d.get("url"));
                    if (StringUtils.isBlank(url)) {
                        continue;
                    }
                    out.append("\n    ").append(StringUtils.defaultString(text(d.get("title"))))
                            .append(" — ").append(url);
                }
            }
        }
    }

    // ---- skill_view / skills_search ----

    private static String projectSkillView(Map<?, ?> data) {
        String content = text(data.get("content"));
        if (StringUtils.isBlank(content)) {
            return null;
        }
        StringBuilder out = new StringBuilder(content.stripTrailing());
        if (Boolean.TRUE.equals(data.get("is_binary"))) {
            out.append("\n[binary file]");
        }
        String file = text(data.get("file"));
        if (StringUtils.isNotBlank(file)) {
            out.insert(0, file + "\n\n");
        }
        return out.toString();
    }

    private static String projectSkillsSearch(Map<?, ?> data) {
        List<?> items = list(data.get("items"));
        if (items.isEmpty()) {
            return "No skills matched";
        }
        StringBuilder out = new StringBuilder();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> row)) {
                return null;
            }
            String name = text(row.get("name"));
            if (StringUtils.isBlank(name)) {
                return null;
            }
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append("- ").append(name);
            String description = text(row.get("description"));
            if (StringUtils.isNotBlank(description)) {
                out.append(": ").append(truncate(description.strip(), SNIPPET_MAX_CHARS));
            }
        }
        return out.toString();
    }

    // ---- Agent / SendMessage / TaskStop ----

    private static String projectAgent(Map<?, ?> data) {
        String content = text(data.get("content"));
        String agentId = text(data.get("agentId"));
        String errorMsg = text(data.get("errorMsg"));
        StringBuilder out = new StringBuilder();
        if (StringUtils.isNotBlank(content)) {
            out.append(content.strip());
        } else if (StringUtils.isBlank(errorMsg)) {
            out.append("(Subagent completed but returned no output.)");
        }
        if (StringUtils.isNotBlank(errorMsg)) {
            if (out.length() > 0) {
                out.append("\n\n");
            }
            out.append("Error: ").append(errorMsg);
        }
        // agentId 是续跑入口：模型要靠它调 SendMessage，必须给可执行提示而不是裸 id。
        if (StringUtils.isNotBlank(agentId)) {
            out.append("\n\nagentId: ").append(agentId)
                    .append(" (use SendMessage with to: '").append(agentId)
                    .append("' to continue this agent)");
        }
        String status = text(data.get("status"));
        if (StringUtils.isNotBlank(status)) {
            out.append("\nstatus: ").append(status);
        }
        Object toolUses = data.get("totalToolUseCount");
        if (toolUses != null) {
            out.append("\ntool_uses: ").append(toolUses);
        }
        if (Boolean.TRUE.equals(data.get("run_in_background"))) {
            out.append("\nrun_in_background: true");
        }
        return out.toString();
    }

    private static String projectSendMessage(Map<?, ?> data) {
        String message = text(data.get("message"));
        if (StringUtils.isBlank(message)) {
            return null;
        }
        StringBuilder out = new StringBuilder(message);
        String agentId = text(data.get("agentId"));
        if (StringUtils.isNotBlank(agentId)) {
            out.append("\nagentId: ").append(agentId);
        }
        if (Boolean.TRUE.equals(data.get("queued"))) {
            out.append("\nqueued: true");
        }
        return out.toString();
    }

    private static String projectTaskStop(Map<?, ?> data) {
        String message = text(data.get("message"));
        if (StringUtils.isBlank(message)) {
            return null;
        }
        StringBuilder out = new StringBuilder(message);
        String taskId = StringUtils.firstNonBlank(text(data.get("task_id")), text(data.get("agentId")));
        if (StringUtils.isNotBlank(taskId)) {
            out.append("\ntask_id: ").append(taskId);
        }
        String status = text(data.get("status"));
        if (StringUtils.isNotBlank(status)) {
            out.append("\nstatus: ").append(status);
        }
        return out.toString();
    }

    // ---- TaskCreate / TaskUpdate / TaskGet / TaskList / TodoWrite ----

    private static String projectTaskRecord(Map<?, ?> data) {
        Object task = data.get("task");
        if (!(task instanceof Map<?, ?> row)) {
            return text(data.get("message"));
        }
        String rendered = renderTask(row);
        String message = text(data.get("message"));
        return StringUtils.isBlank(message) ? rendered : message + "\n" + rendered;
    }

    private static String projectTaskList(Map<?, ?> data) {
        List<?> tasks = list(data.get("tasks"));
        if (tasks.isEmpty()) {
            String message = text(data.get("message"));
            return StringUtils.isBlank(message) ? "(no tasks)" : message;
        }
        StringBuilder out = new StringBuilder();
        for (Object item : tasks) {
            if (!(item instanceof Map<?, ?> row)) {
                continue;
            }
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(renderTask(row));
        }
        return out.toString();
    }

    /** 任务行只保留 id / 状态 / 标题 / 依赖，其余编排元数据模型用不上。 */
    private static String renderTask(Map<?, ?> row) {
        String id = StringUtils.firstNonBlank(text(row.get("id")), text(row.get("task_id")));
        String status = text(row.get("status"));
        String subject = StringUtils.firstNonBlank(text(row.get("subject")), text(row.get("description")));
        StringBuilder out = new StringBuilder("- ");
        if (StringUtils.isNotBlank(id)) {
            out.append('[').append(id).append("] ");
        }
        if (StringUtils.isNotBlank(status)) {
            out.append(status).append(' ');
        }
        out.append(StringUtils.defaultString(subject));
        Object blockedBy = row.get("blockedBy");
        if (blockedBy instanceof List<?> deps && !deps.isEmpty()) {
            out.append(" (blockedBy: ").append(StringUtils.join(deps, ", ")).append(')');
        }
        return out.toString();
    }

    // ---- EnterPlanMode / ExitPlanMode ----

    private static String projectEnterPlanMode(Map<?, ?> data) {
        // instructions 已由 PlanModePromptInjector 注入 system prompt，工具结果里再塞一份等于双份全文。
        String message = text(data.get("message"));
        if (StringUtils.isBlank(message)) {
            return null;
        }
        StringBuilder out = new StringBuilder(message);
        String planFilePath = text(data.get("planFilePath"));
        if (StringUtils.isNotBlank(planFilePath)) {
            out.append("\nplanFilePath: ").append(planFilePath);
        }
        return out.toString();
    }

    private static String projectExitPlanMode(Map<?, ?> data) {
        // message 里已含 approved / feedback / restoredMode 与计划正文，plan 字段是第二份同样的内容。
        String message = text(data.get("message"));
        if (StringUtils.isBlank(message)) {
            return null;
        }
        return message.stripTrailing();
    }

    // ---- AskUserQuestion / RequestDesktopControl ----

    private static String projectAskUserQuestion(Map<?, ?> data) {
        StringBuilder out = new StringBuilder();
        String message = text(data.get("message"));
        if (StringUtils.isNotBlank(message)) {
            out.append(message);
        }
        if (data.get("answers") != null) {
            if (out.length() > 0) {
                out.append("\n\n");
            }
            out.append("answers: ").append(JSON.toJSONString(data.get("answers")));
        }
        String questionId = text(data.get("questionId"));
        if (StringUtils.isNotBlank(questionId)) {
            out.append("\nquestionId: ").append(questionId);
        }
        return out.length() == 0 ? null : out.toString();
    }

    private static String projectDesktopControl(Map<?, ?> data) {
        String message = text(data.get("message"));
        if (StringUtils.isNotBlank(message)) {
            return message;
        }
        StringBuilder out = new StringBuilder();
        String status = text(data.get("status"));
        if (StringUtils.isNotBlank(status)) {
            out.append("status: ").append(status);
        }
        String reason = text(data.get("reason"));
        if (StringUtils.isNotBlank(reason)) {
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append("reason: ").append(reason);
        }
        return out.length() == 0 ? null : out.toString();
    }

    // ---- GenUI canvas ----

    private static String projectEmitUiTree(Map<?, ?> data) {
        // tree 是模型刚提交的入参回显，不重复给。
        String message = text(data.get("message"));
        if (StringUtils.isBlank(message)) {
            return null;
        }
        StringBuilder out = new StringBuilder(message);
        String canvasId = text(data.get("canvasId"));
        if (StringUtils.isNotBlank(canvasId)) {
            out.append("\ncanvasId: ").append(canvasId);
        }
        if (Boolean.TRUE.equals(data.get("salvaged"))) {
            out.append("\n[args salvaged]");
        }
        return out.toString();
    }

    private static String projectEmitUiPatch(Map<?, ?> data) {
        String message = text(data.get("message"));
        if (StringUtils.isBlank(message)) {
            return null;
        }
        StringBuilder out = new StringBuilder(message);
        String canvasId = text(data.get("canvasId"));
        if (StringUtils.isNotBlank(canvasId)) {
            out.append("\ncanvasId: ").append(canvasId);
        }
        appendIfNotZero(out, data, "patchCount", "patches");
        return out.toString();
    }

    private static String projectCanvasPublish(Map<?, ?> data) {
        String message = text(data.get("message"));
        if (StringUtils.isBlank(message)) {
            return null;
        }
        StringBuilder out = new StringBuilder(message);
        String relativePath = text(data.get("relativePath"));
        if (StringUtils.isNotBlank(relativePath)) {
            out.append('\n').append(relativePath);
        }
        String title = text(data.get("title"));
        if (StringUtils.isNotBlank(title)) {
            out.append("\ntitle: ").append(title);
        }
        if (Boolean.TRUE.equals(data.get("salvaged"))) {
            out.append("\n[html salvaged]");
        }
        return out.toString();
    }

    // ---- ToolSearch / ToolDescribe / memory ----

    private static String projectToolSearch(Map<?, ?> data) {
        List<?> matches = list(data.get("matches"));
        StringBuilder out = new StringBuilder();
        if (matches.isEmpty()) {
            out.append("No tools matched.");
        } else {
            for (Object item : matches) {
                if (!(item instanceof Map<?, ?> row)) {
                    return null;
                }
                String name = text(row.get("name"));
                if (StringUtils.isBlank(name)) {
                    continue;
                }
                if (out.length() > 0) {
                    out.append('\n');
                }
                out.append("- ").append(name);
                String description = text(row.get("description"));
                if (StringUtils.isNotBlank(description)) {
                    out.append(": ").append(truncate(description.strip(), SNIPPET_MAX_CHARS));
                }
            }
        }
        String hint = text(data.get("hint"));
        if (StringUtils.isNotBlank(hint)) {
            out.append("\n\n").append(hint);
        }
        return out.toString();
    }

    private static String projectToolDescribe(Map<?, ?> data) {
        String description = text(data.get("description"));
        if (StringUtils.isBlank(description)) {
            return null;
        }
        StringBuilder out = new StringBuilder(description.strip());
        Object parameters = data.get("parameters");
        if (parameters != null) {
            out.append("\n\nparameters: ").append(JSON.toJSONString(parameters));
        }
        return out.toString();
    }

    private static String projectMemory(Map<?, ?> data) {
        String message = text(data.get("message"));
        StringBuilder out = new StringBuilder(StringUtils.defaultString(message));
        Object results = data.get("results");
        if (results instanceof List<?> items && !items.isEmpty()) {
            if (out.length() > 0) {
                out.append("\n\n");
            }
            out.append(JSON.toJSONString(items));
        }
        if (Boolean.TRUE.equals(data.get("staged"))) {
            out.append("\n[staged]");
        }
        if (Boolean.TRUE.equals(data.get("no_change"))) {
            out.append("\n[no change]");
        }
        return out.length() == 0 ? null : out.toString();
    }

    // ---- code_execution / bash ----

    private static String projectCodeExecution(Map<?, ?> data) {
        StringBuilder out = new StringBuilder();
        String stdout = text(data.get("stdout"));
        String stderr = text(data.get("stderr"));
        if (StringUtils.isNotBlank(stdout)) {
            out.append(stdout.stripTrailing());
        }
        if (StringUtils.isNotBlank(stderr)) {
            if (out.length() > 0) {
                out.append("\n\n");
            }
            out.append("stderr:\n").append(stderr.stripTrailing());
        }
        // 失败时 error 才是模型要读的；成功时它恒为空。
        String error = text(data.get("error"));
        if (StringUtils.isNotBlank(error)) {
            if (out.length() > 0) {
                out.append("\n\n");
            }
            out.append("Error: ").append(error.stripTrailing());
        }
        appendPaths(out, data.get("produced_files"), "files");
        return out.length() == 0 ? "(no output)" : out.toString();
    }

    private static String projectBash(Map<?, ?> data) {
        StringBuilder out = new StringBuilder();
        String stdout = text(data.get("stdout"));
        String stderr = text(data.get("stderr"));
        if (StringUtils.isNotBlank(stdout)) {
            out.append(stdout.stripTrailing());
        }
        if (StringUtils.isNotBlank(stderr)) {
            if (out.length() > 0) {
                out.append("\n\n");
            }
            out.append("stderr:\n").append(stderr.stripTrailing());
        }
        appendIfNotZero(out, data, "exit_code", "exit_code");
        if (Boolean.TRUE.equals(data.get("timed_out"))) {
            out.append("\n[timed out]");
        }
        if (Boolean.TRUE.equals(data.get("truncated"))) {
            out.append("\n[output truncated]");
        }
        appendPaths(out, data.get("produced_files"), "files");
        return out.length() == 0 ? "(no output)" : out.toString();
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max).stripTrailing() + "…";
    }

    // ---- host_cli / browser ----

    private static String projectCli(Map<?, ?> data) {
        StringBuilder out = new StringBuilder();
        String stdout = text(data.get("stdout"));
        String stderr = text(data.get("stderr"));
        if (StringUtils.isNotBlank(stdout)) {
            out.append(stdout.stripTrailing());
        }
        if (StringUtils.isNotBlank(stderr)) {
            if (out.length() > 0) {
                out.append("\n\n");
            }
            out.append("stderr:\n").append(stderr.stripTrailing());
        }
        // 只有非默认值才值得占 token；正常退出、未超时、未截断都不说。
        appendIfNotZero(out, data, "exit_code", "exit_code");
        if (Boolean.TRUE.equals(data.get("timed_out"))) {
            out.append("\n[timed out]");
        }
        if (Boolean.TRUE.equals(data.get("truncated"))) {
            out.append("\n[output truncated]");
        }
        String hint = text(data.get("error_hint"));
        if (StringUtils.isNotBlank(hint)) {
            out.append("\nhint: ").append(hint);
        }
        // 会话重建提示（agent_browser）：模型据此知道 CDP 会话是重建的，需要重新导航。
        String notice = text(data.get("notice"));
        if (StringUtils.isNotBlank(notice)) {
            out.append("\nnotice: ").append(notice);
        }
        // 产物引用必须保留：browser 的 screenshot 把 PNG 写进工作区，工具描述让模型用
        // workspace_read 读相对路径 —— 吞掉 artifacts 就等于让模型看不了截的图。
        appendPaths(out, data.get("artifacts"), "files");
        appendPaths(out, data.get("artifact_failures"), "failed files");
        return out.length() == 0 ? "(no output)" : out.toString();
    }

    /** 产物列表只把相对路径摊给模型，URL / size 等留在 toolResult 给前端用。 */
    private static void appendPaths(StringBuilder out, Object artifacts, String label) {
        if (!(artifacts instanceof List<?> items) || items.isEmpty()) {
            return;
        }
        out.append('\n').append(label).append(':');
        for (Object item : items) {
            if (item instanceof Map<?, ?> row) {
                // code_execution / image_generation 用 snake_case 的 file_name，workspace 产物用驼峰。
                String path = StringUtils.firstNonBlank(
                        text(row.get("relativePath")),
                        text(row.get("fileName")),
                        text(row.get("file_name")));
                if (StringUtils.isNotBlank(path)) {
                    out.append("\n- ").append(path);
                }
            }
        }
    }

    private static void appendIfNotZero(StringBuilder out, Map<?, ?> data, String key, String label) {
        int value = intOf(data.get(key), 0);
        if (value != 0) {
            out.append('\n').append(label).append(": ").append(value);
        }
    }

    // ---- helpers ----

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static int intOf(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String str && StringUtils.isNotBlank(str)) {
            try {
                return Integer.parseInt(str.trim());
            } catch (NumberFormatException ignore) {
                return fallback;
            }
        }
        return fallback;
    }

    private static List<?> list(Object value) {
        return value instanceof List<?> items ? items : List.of();
    }
}
