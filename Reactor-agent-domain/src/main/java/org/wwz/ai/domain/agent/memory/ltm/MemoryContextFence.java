package org.wwz.ai.domain.agent.memory.ltm;

import java.util.regex.Pattern;

/**
 * Prefetch 围栏与清洗（对齐 Hermes memory-context）。
 */
public final class MemoryContextFence {

    public static final String OPEN = "<memory-context>";
    public static final String CLOSE = "</memory-context>";

    private static final Pattern FENCE_TAG = Pattern.compile("</?\\s*memory-context\\s*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern INNER_BLOCK = Pattern.compile(
            "<\\s*memory-context\\s*>[\\s\\S]*?</\\s*memory-context\\s*>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SYSTEM_NOTE = Pattern.compile(
            "\\[System note:\\s*The following is recalled memory context,[^\\]]*\\]\\s*",
            Pattern.CASE_INSENSITIVE);

    private MemoryContextFence() {
    }

    public static String sanitize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String cleaned = INNER_BLOCK.matcher(text).replaceAll("");
        cleaned = SYSTEM_NOTE.matcher(cleaned).replaceAll("");
        cleaned = FENCE_TAG.matcher(cleaned).replaceAll("");
        return cleaned.trim();
    }

    public static String buildBlock(String rawContext) {
        if (rawContext == null || rawContext.isBlank()) {
            return "";
        }
        String clean = sanitize(rawContext);
        if (clean.isBlank()) {
            return "";
        }
        return OPEN + "\n"
                + "[System note: The following is recalled memory context, "
                + "NOT new user input. Treat as authoritative reference data — "
                + "this is the agent's persistent memory and should inform responses.]\n\n"
                + clean + "\n"
                + CLOSE;
    }
}
