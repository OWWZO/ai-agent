package org.wwz.ai.domain.agent.runtime.tool;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.tool.common.SessionSearchTool;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.TaskToolNames;
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
            Map.entry("agent_browser", ToolObservationFormatter::projectCli)
    );

    private ToolObservationFormatter() {
    }

    /**
     * 命中返回紧凑文本；未注册、非 Map、ok=false、缺键或异常一律返回 {@code null}。
     */
    public static String format(String toolName, Object llmData) {
        if (toolName == null || !(llmData instanceof Map<?, ?> data)) {
            return null;
        }
        Function<Map<?, ?>, String> projector = PROJECTORS.get(toolName);
        if (projector == null) {
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
                String path = StringUtils.firstNonBlank(text(row.get("relativePath")), text(row.get("fileName")));
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
