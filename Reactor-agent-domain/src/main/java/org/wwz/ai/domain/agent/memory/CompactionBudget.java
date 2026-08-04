package org.wwz.ai.domain.agent.memory;

import lombok.Builder;
import lombok.Value;

/**
 * 会话上下文压缩预算。
 *
 * <p>threshold 是允许输入消息占用的上限，必须先扣除模型输出预留和安全缓冲；
 * 这样压缩器在发起 LLM 摘要前不会把上下文窗口消耗到无法容纳输出的位置。</p>
 */
@Value
@Builder
public class CompactionBudget {

    public static final int DEFAULT_BUFFER_TOKENS = 13_000;
    public static final int DEFAULT_MAX_OUTPUT_RESERVE = 20_000;
    public static final int DEFAULT_KEEP_MIN_TOKENS = 10_000;
    public static final int DEFAULT_KEEP_MIN_TEXT_MESSAGES = 5;
    public static final int DEFAULT_KEEP_MAX_TOKENS = 40_000;
    public static final int DEFAULT_MAX_CONSECUTIVE_FAILURES = 3;
    public static final int DEFAULT_CONTEXT_WINDOW = 100_000;
    public static final int DEFAULT_MAX_OUTPUT_TOKENS = 16_384;
    public static final int DEFAULT_MICRO_KEEP_RECENT_TOOL_RESULTS = 5;
    public static final int DEFAULT_MICRO_TOOL_RESULT_MAX_CHARS = 8_000;
    public static final String CLEARED_TOOL_RESULT =
            "[Old tool result content cleared]";

    boolean enabled;
    boolean llmEnabled;
    boolean microEnabled;
    boolean sessionMemoryEnabled;
    int contextWindow;
    int maxOutputTokens;
    int bufferTokens;
    int maxOutputReserve;
    int keepMinTokens;
    int keepMinTextMessages;
    int keepMaxTokens;
    int maxConsecutiveFailures;
    double temperature;
    int messageContentCharLimit;
    int microKeepRecentToolResults;
    int microToolResultMaxChars;

    public int threshold() {
        // 配置可能来自外部环境，先把负数归零，并至少保留一个可用 token 的输入空间。
        int reservedOut = Math.min(Math.max(maxOutputTokens, 0), Math.max(maxOutputReserve, 0));
        int window = Math.max(contextWindow, 0);
        int buffer = Math.max(bufferTokens, 0);
        return Math.max(window - reservedOut - buffer, 1);
    }

    public static CompactionBudget defaults() {
        // 默认值同时打开完整压缩、LLM 摘要和微压缩，形成生产主路径的基线预算。
        return CompactionBudget.builder()
                .enabled(true)
                .llmEnabled(true)
                .microEnabled(true)
                .sessionMemoryEnabled(true)
                .contextWindow(DEFAULT_CONTEXT_WINDOW)
                .maxOutputTokens(DEFAULT_MAX_OUTPUT_TOKENS)
                .bufferTokens(DEFAULT_BUFFER_TOKENS)
                .maxOutputReserve(DEFAULT_MAX_OUTPUT_RESERVE)
                .keepMinTokens(DEFAULT_KEEP_MIN_TOKENS)
                .keepMinTextMessages(DEFAULT_KEEP_MIN_TEXT_MESSAGES)
                .keepMaxTokens(DEFAULT_KEEP_MAX_TOKENS)
                .maxConsecutiveFailures(DEFAULT_MAX_CONSECUTIVE_FAILURES)
                .temperature(0.2d)
                .messageContentCharLimit(4000)
                .microKeepRecentToolResults(DEFAULT_MICRO_KEEP_RECENT_TOOL_RESULTS)
                .microToolResultMaxChars(DEFAULT_MICRO_TOOL_RESULT_MAX_CHARS)
                .build();
    }
}
