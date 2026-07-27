package org.wwz.ai.domain.agent.runtime.planmode;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.common.AgentDispatchTool;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.TaskToolNames;

import java.util.Locale;
import java.util.Set;

/**
 * Plan Mode 工具门禁（对标 cc-haha：主 agent prompt 约束 + 写操作仅 plan 文件）。
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
            "AskUserQuestion",
            AgentDispatchTool.NAME,
            "workspace_read",
            "workspace_list",
            "workspace_glob",
            "workspace_grep",
            "deep_search",
            "web_fetch",
            "WebFetch",
            "skill_tool",
            "get_html_canvas_guide",
            "get_genui_guide",
            "list_ui_components"
    );

    private static final Set<String> MUTATING = Set.of(
            "workspace_write",
            "workspace_edit",
            "file_tool",
            "code_interpreter",
            "report_tool",
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
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("write") || lower.contains("edit") || lower.contains("delete")
                || lower.contains("exec") || lower.contains("run_command")) {
            return "Plan mode: 工具「" + name + "」可能修改状态，已禁止。请退出 plan mode 并获用户批准后再用。";
        }
        return null;
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
