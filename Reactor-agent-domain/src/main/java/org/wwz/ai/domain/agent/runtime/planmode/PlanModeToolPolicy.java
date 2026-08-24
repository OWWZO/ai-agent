package org.wwz.ai.domain.agent.runtime.planmode;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.common.AgentDispatchTool;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.TaskToolNames;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.dto.tool.McpToolInfo;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Plan Mode 工具门禁（主 agent prompt 约束 + 写操作仅 plan 文件）。
 */
public final class PlanModeToolPolicy {

    private static final Set<String> ALWAYS_ALLOWED = Set.of(
            TaskToolNames.ENTER_PLAN_MODE,
            TaskToolNames.EXIT_PLAN_MODE,
            TaskToolNames.TASK_CREATE,
            TaskToolNames.TASK_GET,
            TaskToolNames.TASK_UPDATE,
            TaskToolNames.TASK_LIST,
            TaskToolNames.TODO_WRITE,
            TaskToolNames.TASK_STOP,
            TaskToolNames.TASK_OUTPUT,
            TaskToolNames.SEND_MESSAGE,
            "AskUserQuestion",
            AgentDispatchTool.NAME,
            "workspace_read",
            "workspace_list",
            "workspace_glob",
            "workspace_grep",
            "skill_tool",
            "get_html_canvas_guide",
            "get_genui_guide",
            "list_ui_components"
    );

    private static final Set<String> SEARCH_TOOLS = Set.of(
            "deep_search",
            "web_search",
            "WebSearch",
            "web_fetch",
            "WebFetch",
            "session_search",
            "twitter",
            "reddit",
            "xueqiu"
    );

    private static final Set<String> MUTATING = Set.of(
            "workspace_write",
            "workspace_edit",
            "file_tool",
            "code_interpreter",
            "report_tool",
            "document_generate",
            "slides_generate",
            "excel_generator",
            "checklist_generate",
            "template_filler",
            "canvas_publish",
            "emit_ui_tree",
            "emit_ui_patch",
            "image_generation",
            "data_analysis",
            "multimodalagent_tool"
    );

    private PlanModeToolPolicy() {
    }

    /**
     * @return null 表示放行；非 null 为拒绝原因（给模型看）
     */
    public static String denyReason(AgentContext context, String toolName, Object args) {
        if (context == null) {
            return null;
        }
        return denyReason(context.getPlanModeState(), toolName, args);
    }

    /**
     * @return null 表示放行；非 null 为拒绝原因（给模型看）
     */
    public static String denyReason(PlanModeState state, String toolName, Object args) {
        if (state == null || !state.isPlanMode() || toolName == null) {
            return null;
        }
        String name = toolName.trim();
        String lower = name.toLowerCase(Locale.ROOT);
        if (SEARCH_TOOLS.contains(name)
                || lower.contains("search")
                || lower.contains("web_fetch")
                || lower.equals("fetch")) {
            return "Plan mode: 禁止调用搜索或网页抓取工具「" + name + "」。"
                    + " 请只使用本地工作区只读工具，退出 plan mode 并获用户批准后再进行外部检索。";
        }
        if (ALWAYS_ALLOWED.contains(name)) {
            // Agent 在 plan 期允许，但应优先 Explore；不在此硬拦
            return null;
        }
        if ("workspace_write".equals(name) || "workspace_edit".equals(name)) {
            String path = extractPath(args);
            if (StringUtils.isNotBlank(path) && isPlanRelativePath(path)) {
                return null;
            }
            return "Plan mode: 只能写入会话计划文件（.reactor/plan.md），禁止修改其它文件。"
                    + " 请把计划写到 .reactor/plan.md，或完成后调用 ExitPlanMode 请求用户批准。";
        }
        if (MUTATING.contains(name)) {
            return "Plan mode: 禁止调用会修改系统状态的工具「" + name + "」。"
                    + " 请只做只读探索、AskUserQuestion、写计划文件，或 ExitPlanMode。";
        }
        // MCP / 未知工具：默认允许只读类；名字含 write/edit/run 则拦
        if (lower.contains("write") || lower.contains("edit") || lower.contains("delete")
                || lower.contains("exec") || lower.contains("run_command")) {
            return "Plan mode: 工具「" + name + "」可能修改状态，已禁止。请退出 plan mode 并获用户批准后再用。";
        }
        return null;
    }

    /**
     * 构造发给 LLM 的 plan-mode 工具视图，避免模型看到被禁止的外部检索工具。
     */
    public static ToolCollection filterTools(AgentContext context, ToolCollection tools) {
        if (context == null || tools == null || !context.requirePlanModeState().isPlanMode()) {
            return tools;
        }
        ToolCollection filtered = new ToolCollection();
        filtered.setAgentContext(context);
        filtered.setMcpToolExecutor(tools.getMcpToolExecutor());
        filtered.restoreTaskScopedState(tools.snapshotTaskScopedState());
        if (tools.getToolMap() != null) {
            for (Map.Entry<String, BaseTool> entry : tools.getToolMap().entrySet()) {
                if (!isSearchTool(entry.getKey())) {
                    filtered.addTool(entry.getValue());
                }
            }
        }
        if (tools.getMcpToolMap() != null) {
            for (McpToolInfo info : tools.getMcpToolMap().values()) {
                if (info != null && !isSearchTool(info.getName())) {
                    filtered.addMcpTool(info);
                }
            }
        }
        return filtered;
    }

    private static boolean isSearchTool(String toolName) {
        if (toolName == null) {
            return false;
        }
        String name = toolName.trim();
        String lower = name.toLowerCase(Locale.ROOT);
        return SEARCH_TOOLS.contains(name)
                || lower.contains("search")
                || lower.contains("web_fetch")
                || lower.equals("fetch");
    }

    private static boolean isPlanRelativePath(String path) {
        String normalized = path.replace('\\', '/').trim();
        return normalized.equals(PlanArtifactStore.RELATIVE_PLAN_PATH)
                || normalized.endsWith("/" + PlanArtifactStore.RELATIVE_PLAN_PATH)
                || normalized.endsWith(".reactor/plan.md");
    }

    @SuppressWarnings("unchecked")
    private static String extractPath(Object args) {
        if (!(args instanceof java.util.Map<?, ?> map)) {
            return null;
        }
        Object path = map.get("path");
        if (path == null) {
            path = map.get("file_path");
        }
        if (path == null) {
            path = map.get("filePath");
        }
        return path == null ? null : String.valueOf(path);
    }
}
