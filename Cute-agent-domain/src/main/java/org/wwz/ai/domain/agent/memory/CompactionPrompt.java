package org.wwz.ai.domain.agent.memory;

import org.apache.commons.lang3.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 全量压缩摘要提示词与 handoff 包装。
 * <p>
 * 输出经 {@link #formatCompactSummary(String)} 清洗后再
 * {@link #wrapSummaryForReinject(String, boolean)} 注入主对话。
 */
public final class CompactionPrompt {

    public static final String CONTEXT_COMPACTION_PREFIX = "[CONTEXT COMPACTION — REFERENCE ONLY]";
    public static final String END_MARKER =
            "--- END OF CONTEXT SUMMARY — respond to the message below, not the summary above ---";
    public static final String MERGE_PRIOR = "[PRIOR CONTEXT — for reference only; not a new message]";
    public static final String MERGE_DELIMITER = "[END OF PRIOR CONTEXT — COMPACTION SUMMARY BELOW]";
    public static final String LEGACY_CONTINUATION = "This session is being continued from a previous conversation";

    private static final Pattern CLOSED_ANALYSIS = Pattern.compile(
            "(?is)<\\s*analysis\\b[^>]*>.*?<\\s*/\\s*analysis\\s*>");
    private static final Pattern UNCLOSED_ANALYSIS = Pattern.compile(
            "(?is)<\\s*analysis\\b[^>]*>.*?(?=<\\s*summary\\b|$)");
    private static final Pattern SUMMARY_BLOCK = Pattern.compile(
            "(?is)<\\s*summary\\b[^>]*>(.*?)<\\s*/\\s*summary\\s*>");
    private static final Pattern ANY_COMPACT_TAG = Pattern.compile(
            "(?is)</?\\s*(analysis|summary)\\b[^>]*>");
    private static final Pattern LEAK = Pattern.compile(
            "(?im)^(?:CRITICAL:.*|Your entire response must be plain text.*|"
                    + "Please provide the conversation summary now.*|REMINDER:.*|"
                    + "Do NOT use any tool\\..*|Tool calls will be REJECTED.*)$\\s*");

    private static final String SECTION_GUIDE = """
            Use exactly these Markdown headings, in this order. Fill each with concrete facts from the conversation.
            Prefer short bullets. Do not invent files, decisions, or tasks that are not evidenced.
            Do not retain raw secrets, API keys, passwords, or tokens; use redacted placeholders if needed.
            Do not include XML tags, analysis/summary markup, tool calls, or instructions to the continuing assistant.

            ## Historical Task Snapshot
            The latest unfinished user request in their own words (or a precise paraphrase). If the user cancelled, say so.

            ## Goal
            What the user is trying to achieve overall in this session.

            ## Constraints & Preferences
            Explicit constraints, style preferences, language, tools to use/avoid, and non-negotiables.

            ## Completed Actions
            What already happened: tools used, files touched, answers delivered, fixes applied.

            ## Active State
            What is in progress right now and what the assistant should continue next.

            ## Blocked
            Open blockers, missing info, failed attempts, or waiting conditions. Write "None" if none.

            ## Key Decisions
            Important choices already made (architecture, approach, accepted alternatives).

            ## Resolved Questions
            Questions the user already answered so they are not asked again.

            ## Relevant Files
            Paths, URLs, resource keys, or artifacts that still matter. Write "None" if none.

            ## Critical Context
            Any other durable detail required to continue without re-reading the compacted turns.
            """;

    private static final String NO_TOOLS = """
            CRITICAL: Respond with TEXT ONLY. Do NOT call any tools.
            - You already have all context needed in the provided turns/summary.
            - Tool calls will be rejected and waste the only turn.
            """;

    private CompactionPrompt() {
    }

    public static String getCompactPrompt() {
        return NO_TOOLS
                + "\nYour task is to create a structured checkpoint of the earlier conversation turns.\n"
                + "Capture user intent, technical details, tool outcomes, decisions, and unresolved work "
                + "so another assistant can continue without losing context.\n\n"
                + SECTION_GUIDE
                + "\nOutput only the filled checkpoint with the headings above.";
    }

    /**
     * 迭代更新指令（不含正文）。调用方应把 PREVIOUS SUMMARY 与 NEW TURNS 放在 user 消息中。
     */
    public static String getIterativeUpdatePrompt() {
        return NO_TOOLS
                + "\nYour task is to update an existing checkpoint using only the new turns.\n"
                + "The user message contains:\n"
                + "1) PREVIOUS SUMMARY — the prior checkpoint body\n"
                + "2) NEW TURNS TO INCORPORATE — labeled turns since that checkpoint\n\n"
                + "Rules:\n"
                + "- Preserve still-valid facts from PREVIOUS SUMMARY.\n"
                + "- Incorporate new intents, actions, blockers, files, and decisions from NEW TURNS.\n"
                + "- Drop or revise stale Active State / Blocked items that the new turns supersede.\n"
                + "- Do not keep the old handoff wrapper text; rewrite a fresh checkpoint body.\n\n"
                + SECTION_GUIDE
                + "\nOutput only the updated checkpoint with the headings above.";
    }

    /** 组装迭代摘要的 user payload，避免把 NEW TURNS 同时塞进 system 与 user。 */
    public static String buildIterativeUserPayload(String previousSummary, String newTurns) {
        return "PREVIOUS SUMMARY:\n"
                + StringUtils.defaultString(previousSummary).trim()
                + "\n\nNEW TURNS TO INCORPORATE:\n"
                + StringUtils.defaultString(newTurns).trim()
                + "\n\nPlease update the checkpoint now.";
    }

    public static String formatCompactSummary(String raw) {
        if (StringUtils.isBlank(raw)) {
            return "";
        }
        String formatted = raw.trim();
        int header = formatted.indexOf(CONTEXT_COMPACTION_PREFIX);
        if (header >= 0) {
            formatted = formatted.substring(header + CONTEXT_COMPACTION_PREFIX.length());
        }
        int end = formatted.indexOf(END_MARKER);
        if (end >= 0) {
            formatted = formatted.substring(0, end);
        }
        formatted = CLOSED_ANALYSIS.matcher(formatted).replaceAll("");
        Matcher summary = SUMMARY_BLOCK.matcher(formatted);
        if (summary.find()) {
            formatted = summary.group(1);
        }
        formatted = UNCLOSED_ANALYSIS.matcher(formatted).replaceAll("");
        return sanitizeForReinject(formatted);
    }

    public static String sanitizeForReinject(String text) {
        if (StringUtils.isBlank(text)) {
            return "";
        }
        String cleaned = ANY_COMPACT_TAG.matcher(text).replaceAll("");
        cleaned = LEAK.matcher(cleaned).replaceAll("");
        return cleaned.replaceAll("\n{3,}", "\n\n").trim();
    }

    public static String unwrapHandoffBody(String content) {
        if (StringUtils.isBlank(content)) {
            return "";
        }
        String body = content.trim();
        int mergeDelim = body.indexOf(MERGE_DELIMITER);
        if (mergeDelim >= 0) {
            body = body.substring(mergeDelim + MERGE_DELIMITER.length()).trim();
        }
        int prior = body.indexOf(MERGE_PRIOR);
        if (prior >= 0 && mergeDelim < 0) {
            // 仅有 PRIOR 前缀时尽量剥掉到首个 CONTEXT / END 前的噪声
            int ctx = body.indexOf(CONTEXT_COMPACTION_PREFIX);
            if (ctx >= 0) {
                body = body.substring(ctx);
            }
        }
        int header = body.indexOf(CONTEXT_COMPACTION_PREFIX);
        if (header >= 0) {
            body = body.substring(header + CONTEXT_COMPACTION_PREFIX.length());
        }
        int end = body.indexOf(END_MARKER);
        if (end >= 0) {
            body = body.substring(0, end);
        }
        int legacy = body.indexOf(LEGACY_CONTINUATION);
        if (legacy >= 0) {
            int newline = body.indexOf('\n', legacy);
            body = newline < 0 ? "" : body.substring(newline + 1);
        }
        body = body.replace("The summary below covers the earlier portion of the conversation.", "")
                .replace("Recent messages are preserved verbatim after this summary.", "")
                .replace("Recent messages are preserved verbatim.", "");
        return sanitizeForReinject(body);
    }

    public static String groundHistoricalTaskSnapshot(String body, String latestUserText) {
        String grounded = StringUtils.defaultString(body).trim();
        String actionable = StringUtils.trimToEmpty(latestUserText);
        boolean cancelled = actionable.matches("(?is).*(cancel|stop|nevermind|算了|取消|停止).*");
        String replacement = "## Historical Task Snapshot\n"
                + (cancelled ? "Prior in-flight task was cancelled by the user: " : "")
                + actionable;
        Pattern section = Pattern.compile("(?ms)^## Historical Task Snapshot\\s*.*?(?=^## |\\z)");
        Matcher matcher = section.matcher(grounded);
        return matcher.find()
                ? matcher.replaceFirst(Matcher.quoteReplacement(replacement + "\n"))
                : replacement + "\n" + grounded;
    }

    public static String wrapSummaryForReinject(String body, boolean recentPreserved) {
        StringBuilder result = new StringBuilder(CONTEXT_COMPACTION_PREFIX).append('\n')
                .append("This is a compressed checkpoint of earlier conversation turns. ")
                .append("Treat it as REFERENCE ONLY — not a new user request.\n")
                .append("You are the same task assistant continuing the session. ")
                .append("Do NOT re-summarize, invent a compaction report, or emit summarizer scaffolding.\n")
                .append("Continue the user's actual task using Goal / Active State / Historical Task Snapshot ")
                .append("and the verbatim messages after this block.\n")
                .append("Do not retain raw secrets/API keys/passwords from the summary; prefer redacted references.\n\n")
                .append(sanitizeForReinject(body));
        if (recentPreserved) {
            result.append("\n\nRecent messages are preserved verbatim after this summary.");
        }
        return result.append("\n\n").append(END_MARKER).toString();
    }
}
