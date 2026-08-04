package org.wwz.ai.domain.agent.memory.ltm;

/**
 * Hermes 风格长期记忆写入规范的唯一文本源。
 *
 * <p>同一组规则会被主 Agent 提示词、{@code memory} 工具 schema、后台复盘 fork、
 * 压缩前 flush fork 和紧凑提醒共同复用，避免不同入口对“什么值得记忆”产生漂移。
 * 记忆快照内容本身由 {@code CuratedMemoryStore#formatSnapshot} 独立负责。</p>
 */
public final class LtmPromptGuidance {

    // -------------------------------------------------------------------------
    // 工具、fork 和主提示词共同引用的写入判定标准
    // -------------------------------------------------------------------------

    /** What belongs in target=user. */
    public static final String TARGET_USER =
            "target=user: who the user is — name, role, preferences, communication style, "
                    + "recurring corrections, work-style expectations";

    /** What belongs in target=curated. */
    public static final String TARGET_CURATED =
            "target=curated: stable environment/project facts — conventions, tool quirks, "
                    + "durable setup lessons (not one-off tasks)";

    /** Priority order when capacity is scarce. */
    public static final String PRIORITY =
            "Priority: user preferences & corrections > environment facts > procedures. "
                    + "The best memory stops the user repeating themselves / re-steering you.";

    /** Hard SKIP list (Hermes + Reactor session_search). */
    public static final String SKIP =
            "SKIP: trivial/obvious info; easily re-discovered facts; raw data dumps; task progress; "
                    + "session outcomes; completed-work logs; temporary TODO; PR/issue numbers; commit SHAs; "
                    + "'fixed bug X' / 'Phase N done' / file counts; unresolved failures; full how-to procedures "
                    + "(skills/SOP). Use session_search for past-turn details. "
                    + "If a fact will be stale in 7 days, it does not belong in memory.";

    /** Style of entry text. */
    public static final String STYLE =
            "Write declarative facts, not self-instructions: "
                    + "'User prefers concise responses' ✓ — 'Always respond concisely' ✗; "
                    + "'Project uses pytest with xdist' ✓ — 'Run tests with pytest -n 4' ✗. "
                    + "Imperative phrasing re-reads as a directive later and can override the current request.";

    // -------------------------------------------------------------------------
    // 主 Agent 系统提示词
    // -------------------------------------------------------------------------

    public static final String MEMORY_GUIDANCE =
            "You have persistent memory across sessions. Save durable facts using the memory "
                    + "tool when appropriate. Memory is injected into every turn — keep entries compact "
                    + "and high-signal.\n"
                    + PRIORITY + "\n"
                    + TARGET_USER + ". " + TARGET_CURATED + ".\n"
                    + "Do NOT save task progress, session outcomes, completed-work logs, or temporary TODO "
                    + "state; use session_search for those. "
                    + "Specifically: do not record PR numbers, issue numbers, commit SHAs, 'fixed bug X', "
                    + "'submitted PR Y', 'Phase N done', file counts, or anything stale within 7 days.\n"
                    + STYLE + " Procedures and workflows belong in skills/SOP, not memory.";

    public static final String SESSION_SEARCH_GUIDANCE =
            "When the user references something from a past conversation or you suspect "
                    + "relevant cross-session context exists, use session_search to recall it before "
                    + "asking them to repeat themselves.";

    // -------------------------------------------------------------------------
    // memory 工具 schema 描述
    // -------------------------------------------------------------------------

    public static final String MEMORY_TOOL_DESCRIPTION = """
            Save durable facts to persistent memory that survive across sessions. Memory is \
            injected into every future turn, so keep entries compact and high-signal.

            HOW: make ALL your changes in ONE call via an 'operations' array (each item: \
            {action, content?, old_text?}). Prefer batch when consolidating. The response \
            reports current/limit chars; one successful call finishes the update — don't repeat. \
            Use bare action/content/old_text only for a single lone change. \
            replace/remove match by a short unique substring in old_text.

            WHEN: save proactively when the user states a preference, correction, or personal \
            detail, or you learn a stable fact about their environment, conventions, or workflow. \
            %s

            IF FULL: an add is rejected with used/limit chars. Reissue as ONE batch that removes \
            or shortens enough stale entries and adds the new one together — never silent drop.

            TARGETS: %s. %s.

            %s %s
            """.formatted(PRIORITY, TARGET_USER, TARGET_CURATED, SKIP, STYLE);

    // -------------------------------------------------------------------------
    // 后台复盘和压缩前抢救 fork 指令
    // -------------------------------------------------------------------------

    /**
     * Port of Hermes {@code _MEMORY_REVIEW_PROMPT}, plus Reactor targets / shared SKIP+STYLE.
     */
    public static final String REVIEW_DIRECTIVE =
            "Review the conversation above and consider saving to memory if appropriate.\n\n"
                    + "Focus on:\n"
                    + "1. Has the user revealed things about themselves — their persona, desires, "
                    + "preferences, or personal details worth remembering? → memory tool target=user\n"
                    + "2. Has the user expressed expectations about how you should behave, their work "
                    + "style, or ways they want you to operate? → memory tool target=user\n"
                    + "3. Did you learn a stable environment/project convention or tool quirk that will "
                    + "still matter next session? → memory tool target=curated\n\n"
                    + PRIORITY + "\n"
                    + STYLE + "\n"
                    + SKIP + "\n\n"
                    + "If something stands out, save it using the memory tool only. "
                    + "If nothing new is worth saving, say 'Nothing to save.' and stop without tool calls.";

    /**
     * Pre-compress salvage: same write criteria, urgent timing.
     */
    public static final String FLUSH_DIRECTIVE =
            "The conversation window above is about to be compacted. "
                    + "Save durable memories NOW via the memory tool only before they are lost.\n\n"
                    + "Focus on the same criteria as a memory review:\n"
                    + "1. User persona / preferences / personal details → target=user\n"
                    + "2. Behavioral or work-style expectations → target=user\n"
                    + "3. Stable environment/project conventions → target=curated\n\n"
                    + PRIORITY + "\n"
                    + STYLE + "\n"
                    + SKIP + "\n\n"
                    + "If nothing durable is missing from memory, call nothing and finish.";

    /** Appended to parent system (or used alone) for memory-only forks. */
    public static final String FORK_SYSTEM_DIRECTIVE =
            "# LTM fork directive\n"
                    + "You may ONLY use the memory tool. Do not call any other tools.\n"
                    + MEMORY_GUIDANCE;

    /** Inline nudge when main agent is about to compact (still has full tools). */
    public static final String FLUSH_INLINE_NUDGE =
            "Context is about to be compacted. If any durable user preferences, identity facts, "
                    + "or environment conventions are not yet saved, call the memory tool "
                    + "(target=user|curated) now before they are lost. "
                    + PRIORITY + " " + SKIP;

    public static final String POST_COMPACT_REMINDER =
            "Older turns were compacted into a shorter working memory. "
                    + "You remain the task assistant — do NOT emit analysis/summary XML markup "
                    + "and do NOT re-run conversation summarization. "
                    + "Use memory tool for durable facts (preferences, corrections, stable conventions); "
                    + "use session_search for details still in the execution ledger. "
                    + SKIP;

    private LtmPromptGuidance() {
    }

    public static String forLoadedTools(boolean memoryToolPresent, boolean sessionSearchToolPresent) {
        if (!memoryToolPresent && !sessionSearchToolPresent) {
            return "";
        }
        // 只注入当前实际加载的工具规则，避免提示词宣称了不可调用的能力。
        StringBuilder sb = new StringBuilder();
        if (memoryToolPresent) {
            sb.append(MEMORY_GUIDANCE);
        }
        if (sessionSearchToolPresent) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(SESSION_SEARCH_GUIDANCE);
        }
        return sb.toString();
    }

    /**
     * System prompt for a memory-only fork: parent prefix (cache-friendly) + write standards.
     */
    public static String forkSystemPrompt(String parentSystemPrompt) {
        if (parentSystemPrompt == null || parentSystemPrompt.isBlank()) {
            return FORK_SYSTEM_DIRECTIVE;
        }
        // 追加前检查幂等标记，避免 fork 被重复包装导致提示词不断增长。
        String parent = parentSystemPrompt.trim();
        if (parent.contains(FORK_SYSTEM_DIRECTIVE) || parent.contains("# LTM fork directive")) {
            return parent;
        }
        return parent + "\n\n" + FORK_SYSTEM_DIRECTIVE;
    }
}
