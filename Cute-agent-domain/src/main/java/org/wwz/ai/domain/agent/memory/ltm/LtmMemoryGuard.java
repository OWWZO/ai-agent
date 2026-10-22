package org.wwz.ai.domain.agent.memory.ltm;

import org.wwz.ai.domain.agent.runtime.agent.AgentContext;

import java.util.Locale;
import java.util.Set;

/**
 * skip_memory 守卫：子代理 / cron / flush / review 等路径禁止写用户长期画像。
 */
public final class LtmMemoryGuard {

    /** 写记忆相关工具名（子代理工具池默认剥离） */
    public static final Set<String> MEMORY_WRITE_TOOLS = Set.of(
            "memory",
            "fact_store",
            "viking_memory"
    );

    private LtmMemoryGuard() {
    }

    public static boolean isSkipMemory(AgentContext context) {
        return context != null && Boolean.TRUE.equals(context.getSkipMemory());
    }

    /** flush/review fork：禁止再触发 sync/review 调度 */
    public static boolean isSideEffectsDisabled(AgentContext context) {
        return context != null && Boolean.TRUE.equals(context.getLtmSideEffectsDisabled());
    }

    public static boolean isMemoryWriteTool(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return false;
        }
        return MEMORY_WRITE_TOOLS.contains(toolName.trim().toLowerCase(Locale.ROOT));
    }

    public static String deniedMessage() {
        return "skip_memory: long-term memory writes are disabled in this execution context "
                + "(subagent/cron/flush/review). Do not call memory tools.";
    }
}
