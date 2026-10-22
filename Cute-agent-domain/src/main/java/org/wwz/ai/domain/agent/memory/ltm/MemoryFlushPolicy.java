package org.wwz.ai.domain.agent.memory.ltm;

import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.enums.RoleType;

import java.util.ArrayList;
import java.util.List;

/**
 * 压缩前 Memory Flush 策略。
 */
public final class MemoryFlushPolicy {

    public static final String FLUSH_NOTE_PREFIX = "[memory-flush]";

    private MemoryFlushPolicy() {
    }

    public static boolean shouldFlush(int userTurnCount, int flushMinTurns, boolean willCompact) {
        return flushMinTurns > 0 && willCompact && userTurnCount >= flushMinTurns;
    }

    public static int countUserTurns(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (Message message : messages) {
            if (message != null && message.getRole() == RoleType.USER) {
                n++;
            }
        }
        return n;
    }

    /**
     * 在即将压缩的列表前插入 flush 提示，便于模型本轮优先 memory tool 抢救（若压缩发生在 turn 前，则保留为压缩后首条提醒）。
     */
    public static List<Message> prependFlushNudge(List<Message> messages) {
        if (messages == null) {
            return List.of();
        }
        List<Message> out = new ArrayList<>(messages.size() + 1);
        out.add(Message.userMessage(
                FLUSH_NOTE_PREFIX + " " + LtmPromptGuidance.FLUSH_INLINE_NUDGE,
                null));
        out.addAll(messages);
        return out;
    }

    public static List<Message> prependPostCompactReminder(List<Message> compacted) {
        if (compacted == null) {
            return List.of();
        }
        List<Message> out = new ArrayList<>(compacted.size() + 1);
        out.add(Message.userMessage(
                FLUSH_NOTE_PREFIX + " " + LtmPromptGuidance.POST_COMPACT_REMINDER,
                null));
        out.addAll(compacted);
        return out;
    }
}
