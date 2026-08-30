package org.wwz.ai.domain.agent.ledger.model.tooloutput;

import java.util.Set;

/**
 * rich tool 输出名常量及当前持久化边界。
 * 统一收口路由和识别逻辑，避免各处散落硬编码字符串。
 */
public final class ToolOutputNames {

    public static final String DEEP_SEARCH = "deep_search";
    public static final String FILE_TOOL = "file_tool";
    public static final String CODE_INTERPRETER = "code_interpreter";
    public static final String REPORT_TOOL = "report_tool";
    /** Structured tool name; not yet a dedicated output table (artifact + projector). */
    public static final String CANVAS_PUBLISH = "canvas_publish";
    public static final String EMIT_UI_TREE = "emit_ui_tree";
    public static final String EMIT_UI_PATCH = "emit_ui_patch";
    public static final String DATA_ANALYSIS = "data_analysis";
    public static final String MULTIMODAL_AGENT = "multimodalagent_tool";
    public static final String IMAGE_GENERATION = "image_generation_tool";
    public static final String SCRIPT_RUNNER = "script_runner_tool";
    public static final String PLANNING = "planning";

    /** 当前仍落到专用数据库输出表的 rich tool。 */
    public static final Set<String> PERSISTED_TOOL_NAMES = Set.of(
            DEEP_SEARCH,
            CODE_INTERPRETER,
            DATA_ANALYSIS,
            MULTIMODAL_AGENT,
            IMAGE_GENERATION,
            CANVAS_PUBLISH,
            EMIT_UI_TREE,
            EMIT_UI_PATCH
    );

    private ToolOutputNames() {
    }

    public static boolean isPersistedTool(String toolName) {
        return PERSISTED_TOOL_NAMES.contains(toolName);
    }
}
