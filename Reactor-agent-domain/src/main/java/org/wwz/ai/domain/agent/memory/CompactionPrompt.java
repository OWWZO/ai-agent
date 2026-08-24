package org.wwz.ai.domain.agent.memory;

import org.apache.commons.lang3.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 压缩摘要提示词。
 * <p>
 * 压缩 LLM 的输出必须经 {@link #formatCompactSummary(String)} 清洗后再
 * {@link #wrapSummaryForReinject(String, boolean)} 注入主对话；否则主 Agent 会把
 * &lt;analysis&gt;/&lt;summary&gt; 结构误当成自身任务格式。
 */
public final class CompactionPrompt {

    private CompactionPrompt() {
    }

    private static final Pattern CLOSED_ANALYSIS = Pattern.compile(
            "(?is)<\\s*analysis\\b[^>]*>.*?<\\s*/\\s*analysis\\s*>");
    private static final Pattern UNCLOSED_ANALYSIS_TO_SUMMARY = Pattern.compile(
            "(?is)<\\s*analysis\\b[^>]*>.*?(?=<\\s*summary\\b|$)");
    private static final Pattern SUMMARY_BLOCK = Pattern.compile(
            "(?is)<\\s*summary\\b[^>]*>(.*?)<\\s*/\\s*summary\\s*>");
    private static final Pattern ANY_COMPACT_TAG = Pattern.compile(
            "(?is)</?\\s*(analysis|summary)\\b[^>]*>");
    private static final Pattern COMPACT_INSTRUCTION_LEAK = Pattern.compile(
            "(?is)(?:CRITICAL:\\s*Respond with TEXT ONLY[^\n]*\n?|"
                    + "Your entire response must be plain text[^\n]*\n?|"
                    + "Please provide the conversation summary now[^\n]*\n?|"
                    + "REMINDER:\\s*Do NOT call any tools[^\n]*\n?|"
                    + "Do NOT use any tool\\.[^\n]*\n?|"
                    + "Tool calls will be REJECTED[^\n]*\n?)");

    private static final String NO_TOOLS_PREAMBLE = """
            CRITICAL: Respond with TEXT ONLY. Do NOT call any tools.

            - Do NOT use any tool.
            - You already have all the context you need in the conversation above.
            - Tool calls will be REJECTED and will waste your only turn — you will fail the task.
            - Your entire response must be plain text: an <analysis> block followed by a <summary> block.

            """;

    private static final String DETAILED_ANALYSIS = """
            Before providing your final summary, wrap your analysis in <analysis> tags to organize your thoughts and ensure you've covered all necessary points. In your analysis process:

            1. Chronologically analyze each message and section of the conversation. For each section thoroughly identify:
               - The user's explicit requests and intents
               - Your approach to addressing the user's requests
               - Key decisions, technical concepts and code patterns
               - Specific details like file names, code snippets, function signatures
               - Errors that you ran into and how you fixed them
               - Pay special attention to specific user feedback that you received
            2. Double-check for technical accuracy and completeness.
            """;

    private static final String BASE_COMPACT_PROMPT = """
            Your task is to create a detailed summary of the conversation so far, paying close attention to the user's explicit requests and your previous actions.
            This summary should be thorough in capturing technical details, decisions, tool results, and architectural choices that would be essential for continuing work without losing context.

            """ + DETAILED_ANALYSIS + """

            Your summary should include the following sections:

            1. Primary Request and Intent: Capture all of the user's explicit requests and intents in detail
            2. Key Technical Concepts: List all important technical concepts, technologies, and frameworks discussed.
            3. Files and Code Sections: Enumerate specific files and code sections examined, modified, or created. Include important snippets and why they matter.
            4. Errors and fixes: List all errors that you ran into, and how you fixed them. Pay special attention to user feedback.
            5. Problem Solving: Document problems solved and any ongoing troubleshooting efforts.
            6. All user messages: List ALL user messages that are not tool results. These are critical for understanding feedback and changing intent.
            7. Pending Tasks: Outline any pending tasks that you have explicitly been asked to work on.
            8. Current Work: Describe in detail precisely what was being worked on immediately before this summary request.
            9. Optional Next Step: List the next step related to the most recent work. If listing a next step, include direct quotes from the most recent conversation.

            Here's an example of how your output should be structured:

            <example>
            <analysis>
            [Your thought process]
            </analysis>

            <summary>
            1. Primary Request and Intent:
               [Detailed description]

            2. Key Technical Concepts:
               - [Concept 1]

            3. Files and Code Sections:
               - [File Name 1]
                 - [Why important]
                 - [Important snippet]

            4. Errors and fixes:
                - [Error]:
                  - [Fix]

            5. Problem Solving:
               [Description]

            6. All user messages:
                - [User message]

            7. Pending Tasks:
               - [Task 1]

            8. Current Work:
               [Precise description]

            9. Optional Next Step:
               [Optional next step]
            </summary>
            </example>

            Please provide your summary based on the conversation so far, following this structure and ensuring precision and thoroughness.
            """;

    private static final String NO_TOOLS_TRAILER =
            "\n\nREMINDER: Do NOT call any tools. Respond with plain text only — "
                    + "an <analysis> block followed by a <summary> block. "
                    + "Tool calls will be rejected and you will fail the task.";

    public static String getCompactPrompt() {
        return NO_TOOLS_PREAMBLE + BASE_COMPACT_PROMPT + NO_TOOLS_TRAILER;
    }

    /**
     * 剥离 &lt;analysis&gt; 草稿区，提取 &lt;summary&gt; 正文，并清除残留标签/压缩指令泄漏。
     * 结果只应作为历史上下文注入主对话，不得再含压缩任务格式。
     */
    public static String formatCompactSummary(String raw) {
        if (StringUtils.isBlank(raw)) {
            return "";
        }
        String formatted = CLOSED_ANALYSIS.matcher(raw).replaceAll("");
        // 模型常漏写 </analysis>：删掉 summary 之前（或文末）的未闭合 analysis
        formatted = UNCLOSED_ANALYSIS_TO_SUMMARY.matcher(formatted).replaceAll("");

        Matcher matcher = SUMMARY_BLOCK.matcher(formatted);
        if (matcher.find()) {
            String body = matcher.group(1) == null ? "" : matcher.group(1).trim();
            formatted = matcher.replaceFirst(Matcher.quoteReplacement(body));
        }

        formatted = sanitizeForReinject(formatted);
        return formatted.trim();
    }

    /**
     * 注入主对话前的最终清洗：去标签、去压缩指令泄漏、压空白。
     */
    public static String sanitizeForReinject(String text) {
        if (StringUtils.isBlank(text)) {
            return "";
        }
        String cleaned = ANY_COMPACT_TAG.matcher(text).replaceAll("");
        cleaned = COMPACT_INSTRUCTION_LEAK.matcher(cleaned).replaceAll("");
        cleaned = cleaned.replaceAll("\n{3,}", "\n\n");
        return cleaned.trim();
    }

    /**
     * 压缩后注入上下文的 user 摘要包装（对齐 getCompactUserSummaryMessage）。
     * 明确角色边界，避免主 Agent 模仿压缩 LLM 输出 &lt;analysis&gt;/&lt;summary&gt;。
     */
    public static String wrapSummaryForReinject(String formattedSummary, boolean recentMessagesPreserved) {
        String body = sanitizeForReinject(StringUtils.defaultString(formattedSummary));
        StringBuilder sb = new StringBuilder();
        sb.append("This session is being continued from a previous conversation that ran out of context. ");
        sb.append("The summary below covers the earlier portion of the conversation.\n\n");
        sb.append("IMPORTANT — role boundary for you (the assistant continuing this session):\n");
        sb.append("- You are the same task assistant as before, NOT a context-compaction / summarizer agent.\n");
        sb.append("- Do NOT emit analysis/summary XML markup, compaction reports, or summarizer scaffolding.\n");
        sb.append("- Do NOT re-summarize the conversation or invent a new compaction report.\n");
        sb.append("- Continue the user's actual task from Pending Tasks / Current Work / recent messages.\n\n");
        sb.append(body);
        if (recentMessagesPreserved) {
            sb.append("\n\nRecent messages are preserved verbatim after this summary.");
        }
        return sb.toString();
    }
}
