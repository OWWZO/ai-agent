package org.wwz.ai.domain.agent.runtime.planmode;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.agent.BaseAgent;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.prompt.PlanSolvePrompt;

/**
 * Plan Mode 提示注入。
 */
public final class PlanModePromptInjector {

    public static final String PLAN_MODE_INSTRUCTIONS_MARKER = "PLAN_MODE_INSTRUCTIONS_V2";
    public static final String PLAN_MODE_SPARSE_MARKER = "PLAN_MODE_SPARSE_V2";
    public static final String PLAN_MODE_EXIT_MARKER = "PLAN_MODE_EXIT_V1";

    /** 每 N 步注入一次 sparse/full 提醒。 */
    public static final int STEPS_BETWEEN_ATTACHMENTS = 5;
    /** 每 N 次附件用一次完整指引，其余 sparse */
    public static final int FULL_EVERY_N_ATTACHMENTS = 5;

    /**
     * 进入 plan 后硬只读，覆盖其它指令。
     */
    public static final String PLAN_MODE_INSTRUCTIONS = """
            Plan mode is active. The user indicated that they do not want you to execute yet -- you MUST NOT make any edits (with the exception of the plan file mentioned below), run any non-readonly tools (including changing configs or making commits), or otherwise make any changes to the system. This supercedes any other instructions you have received. (%s)

            ## Plan File
            - Write/edit ONLY `.cute/plan.md` (workspace_write path=.cute/plan.md). This is the only file you may modify.
            - Build the plan incrementally in that file. Prefer editing the existing plan over rewriting from scratch when it already exists.

            ## Hard constraints
            - NO business code/config/data edits. NO report/image/script side effects.
              - Read-only tools OK: workspace_read/list/glob/grep, skill_view/skills_search, and external search (web_search / deep_search / web_fetch). Reading is never restricted; only writes and local commands are.
              - Plan mode constrains only the main agent. Agent subagents keep their own tool pool and may search or write files.
            - Clarify with AskUserQuestion when needed. NEVER use AskUserQuestion to ask "is the plan OK?" — that is ExitPlanMode's job.
            - When the plan is ready, call ExitPlanMode (optionally pass plan text). The system will WAIT for user approval; you cannot self-approve.

            ## Workflow
             1. Understand the request (read-only exploration; use an Agent subtask if helpful)
            2. Design approach and note critical files
            3. AskUserQuestion only for real requirement ambiguities
            4. Write the final plan to `.cute/plan.md`
            5. ExitPlanMode for approval — then implement only after approval

            Pure Q&A with no implementation: you may answer in natural language without tools, still without editing files.
            """.formatted(PLAN_MODE_INSTRUCTIONS_MARKER);

    public static final String PLAN_MODE_SPARSE = """
            <system-reminder>
            (%s) Plan mode still active. Read-only except plan file (.cute/plan.md). End turns with AskUserQuestion (clarifications) or ExitPlanMode (plan approval). Never self-approve. Never edit business files.
            </system-reminder>
            """.formatted(PLAN_MODE_SPARSE_MARKER);

    public static final String PLAN_MODE_EXIT = """
            <system-reminder>
            (%s) You have exited plan mode. You can now make edits, run tools, and implement.
            Start with TaskCreate / TodoWrite if the plan has multiple steps, then implement the approved plan.
            The approved plan text is in the previous ExitPlanMode tool result (## Approved Plan).
            </system-reminder>
            """.formatted(PLAN_MODE_EXIT_MARKER);

    private PlanModePromptInjector() {
    }

    public static String ensurePlanModeInstructions(String systemPrompt) {
        String base = systemPrompt == null ? "" : systemPrompt.replace("\r\n", "\n").replace('\r', '\n');
        // 去掉旧 V1 标记块，避免双份
        if (base.contains(PLAN_MODE_INSTRUCTIONS_MARKER)) {
            return base;
        }
        if (base.contains("PLAN_MODE_INSTRUCTIONS_V1")) {
            // 仍补 V2 全文（幂等靠 V2 marker）
        }
        String block = PLAN_MODE_INSTRUCTIONS.trim();
        if (base.isBlank()) {
            return block + "\n";
        }
        return base.trim() + "\n\n" + block + "\n";
    }

    public static void applyIfPlanMode(AgentContext context, BaseAgent agent) {
        if (context == null || agent == null || context.isSubAgent()) {
            return;
        }
        PlanModeState state = context.getPlanModeState();
        if (state == null || !state.isPlanMode()) {
            return;
        }
        agent.setSystemPrompt(ensurePlanModeInstructions(agent.getSystemPrompt()));
    }

    /**
     * 每步前调用：按 throttle 注入 sparse/full 提醒，或注入 plan_mode_exit。
     * <p>
     * 全文只在 system 里没有契约时（system 注入失败）才发消息版，避免进入 plan 第一步就双份。
     */
    public static void injectStepReminders(BaseAgent agent) {
        if (agent == null || agent.getContext() == null || agent.getMemory() == null) {
            return;
        }
        AgentContext context = agent.getContext();
        if (context.isSubAgent()) {
            return;
        }
        PlanModeState state = context.getPlanModeState();
        if (state == null) {
            return;
        }

        if (state.isNeedsPlanModeExitAttachment() && !state.isPlanMode()) {
            agent.getMemory().addMessage(Message.userMessage(PLAN_MODE_EXIT.trim(), null));
            state.clearExitAttachmentFlag();
            return;
        }

        if (!state.isPlanMode()) {
            return;
        }

        applyIfPlanMode(context, agent);

        state.tickStep();
        int steps = state.getStepsSincePlanAttachment();
        boolean first = state.getPlanAttachmentCount() == 0;
        if (!first && steps < STEPS_BETWEEN_ATTACHMENTS) {
            return;
        }

        int attachments = state.getPlanAttachmentCount();
        // system 已带完整契约时，提醒一律用 sparse：同一时刻再塞一份全文等于双份，
        // 而且这条消息会被持久化、之后每轮重发。只有 system 没注入成功才用全文兜底。
        boolean useFull = shouldAttachFull(agent.getSystemPrompt(), attachments);
        String body = useFull
                ? "<system-reminder>\n" + PLAN_MODE_INSTRUCTIONS.trim() + "\n</system-reminder>"
                : PLAN_MODE_SPARSE.trim();
        agent.getMemory().addMessage(Message.userMessage(body, null));
        state.markPlanAttachmentInjected();
    }

    /**
     * 本次提醒是否要带全文。
     *
     * @param systemPrompt 当前 system（用于判断契约是否已在 system 里）
     * @param attachments  已注入的提醒条数（首条为 0）
     */
    public static boolean shouldAttachFull(String systemPrompt, int attachments) {
        boolean systemHasContract = StringUtils.contains(systemPrompt, PLAN_MODE_INSTRUCTIONS_MARKER);
        return !systemHasContract
                || (attachments > 0 && attachments % FULL_EVERY_N_ATTACHMENTS == 0);
    }

    public static String buildApprovedPlanToolResult(String planContent, String planFilePath, String restoredMode) {
        StringBuilder sb = new StringBuilder();
        sb.append("User has approved your plan. You can now start coding.\n");
        sb.append("Start with updating your todo list (TaskCreate / TodoWrite) if applicable.\n");
        sb.append("Restored mode=").append(StringUtils.defaultIfBlank(restoredMode, "default")).append(".\n");
        if (StringUtils.isNotBlank(planFilePath)) {
            // 只展示相对/虚拟路径；若误传绝对路径则脱敏
            String visible = planFilePath.replace('\\', '/');
            if (visible.matches("(?i)^[a-z]:/.*") || visible.startsWith("/Users/") || visible.startsWith("/home/")) {
                visible = org.wwz.ai.domain.agent.runtime.planmode.PlanArtifactStore.RELATIVE_PLAN_PATH;
            }
            sb.append("Plan saved to: ").append(visible).append("\n");
        }
        sb.append("\n## Approved Plan:\n\n");
        sb.append(StringUtils.defaultString(planContent));
        sb.append("\n");
        return sb.toString();
    }

    public static String buildRejectedPlanToolResult(String feedback) {
        return "User rejected the plan and remains in plan mode.\n"
                + "Feedback: " + StringUtils.defaultIfBlank(feedback, "(none)") + "\n"
                + "Please revise the plan based on the feedback and call ExitPlanMode again when ready.\n";
    }

    public static String ensurePlanSolveWithPlanModeGuidance(String systemPrompt) {
        return ensurePlanModeInstructions(PlanSolvePrompt.ensureOrchestration(systemPrompt));
    }
}
