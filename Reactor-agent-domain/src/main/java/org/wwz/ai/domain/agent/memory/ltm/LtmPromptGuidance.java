package org.wwz.ai.domain.agent.memory.ltm;

/**
 * 长期记忆写入规范的唯一文本源。
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

    /** Hard SKIP list. */
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

    /** Flush fork：仅 memory。 */
    public static final String FORK_RUNTIME_TOOL_NOTE_MEMORY =
            "You can only call the memory tool. Other tools remain listed for request parity "
                    + "but will be denied at runtime — do not attempt them.";

    /** Review fork：memory + skill 策展工具（workspace_*、skill_tool、bash）。 */
    public static final String FORK_RUNTIME_TOOL_NOTE_CURATOR =
            "You may only call curator tools that succeed at runtime: memory, workspace_*, "
                    + "skill_tool, and bash (Skill Creator / skill scripts). "
                    + "Other tools remain listed for request parity but will be denied — do not attempt them.";

    /** @deprecated 使用 {@link #FORK_RUNTIME_TOOL_NOTE_MEMORY} / {@link #FORK_RUNTIME_TOOL_NOTE_CURATOR} */
    @Deprecated
    public static final String FORK_RUNTIME_TOOL_NOTE = FORK_RUNTIME_TOOL_NOTE_CURATOR;

    public static final String REVIEW_SKILL_GUIDANCE =
            "Also update the skill library when warranted. Be ACTIVE — reusable workflows belong in "
                    + "skills, not memory.\n"
                    + "How (Reactor):\n"
                    + "  • Inspect with skill_tool / workspace_list|glob|grep|read under skills/\n"
                    + "  • Create or patch via workspace_write|edit on skills/<name>/SKILL.md "
                    + "(and scripts/, references/, templates/ as needed)\n"
                    + "  • Or run Skill Creator via bash when that is the established workflow\n"
                    + "Prefer: (1) patch a skill already used this session, (2) extend an existing "
                    + "class-level skill, (3) add support files, (4) create a new class-level skill.\n"
                    + "Skill names must be class-level — not one-off PR/error/session artifacts.\n"
                    + "Do NOT put task progress or ephemeral TODOs into skills; those stay out of memory too.";

    public static final String REVIEW_DIRECTIVE =
            "Review the conversation above and improve durable memory and/or skills if appropriate.\n\n"
                    + "Memory focus:\n"
                    + "1. User persona, desires, preferences, personal details → memory target=user\n"
                    + "2. Behavioral / work-style expectations → memory target=user\n"
                    + "3. Stable environment/project conventions or tool quirks → memory target=curated\n\n"
                    + PRIORITY + "\n"
                    + STYLE + "\n"
                    + SKIP + "\n\n"
                    + REVIEW_SKILL_GUIDANCE + "\n\n"
                    + FORK_RUNTIME_TOOL_NOTE_CURATOR + "\n"
                    + "If something stands out, save it (memory and/or skills). "
                    + "If nothing new is worth saving, say 'Nothing to save.' and stop without tool calls.";

    /**
     * Pre-compress salvage: memory only, urgent timing.
     */
    public static final String FLUSH_DIRECTIVE =
            "The conversation window above is about to be compacted. "
                    + "Save durable memories NOW via the memory tool before they are lost.\n\n"
                    + "Focus on the same criteria as a memory review:\n"
                    + "1. User persona / preferences / personal details → target=user\n"
                    + "2. Behavioral or work-style expectations → target=user\n"
                    + "3. Stable environment/project conventions → target=curated\n\n"
                    + PRIORITY + "\n"
                    + STYLE + "\n"
                    + SKIP + "\n\n"
                    + FORK_RUNTIME_TOOL_NOTE_MEMORY + "\n"
                    + "If nothing durable is missing from memory, call nothing and finish.";

    /**
     * @deprecated Hermes 对齐后禁止改写父 system；保留常量以免旧测试/引用断裂。
     */
    @Deprecated
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
     * Hermes 对齐：fork 必须原样复用父 system，禁止追加 directive（directive 只进尾部 user）。
     *
     * @return 父 system 原样；空则 null（调用方走默认底座，视为冷缓存）
     */
    public static String forkSystemPrompt(String parentSystemPrompt) {
        if (parentSystemPrompt == null || parentSystemPrompt.isBlank()) {
            return null;
        }
        return parentSystemPrompt;
    }
}
