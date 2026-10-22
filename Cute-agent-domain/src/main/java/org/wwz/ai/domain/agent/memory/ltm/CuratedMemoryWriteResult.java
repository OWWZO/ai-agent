package org.wwz.ai.domain.agent.memory.ltm;

import lombok.Builder;
import lombok.Value;

/**
 * 长期记忆策展写入结果。
 *
 * success 表示请求处理完成，staged/noChange 用于区分“暂存待审核”和“内容未变化”；
 * 调用方据此决定是否继续刷新索引或向用户展示变更提示。
 */
@Value
@Builder
public class CuratedMemoryWriteResult {
    boolean success;
    boolean staged;
    boolean noChange;
    String message;
    int usedChars;
    int limitChars;

    public static CuratedMemoryWriteResult ok(String message, int used, int limit) {
        return CuratedMemoryWriteResult.builder()
                .success(true)
                .message(message)
                .usedChars(used)
                .limitChars(limit)
                .build();
    }

    public static CuratedMemoryWriteResult noChange(String message, int used, int limit) {
        return CuratedMemoryWriteResult.builder()
                .success(true)
                .noChange(true)
                .message(message)
                .usedChars(used)
                .limitChars(limit)
                .build();
    }

    public static CuratedMemoryWriteResult fail(String message, int used, int limit) {
        return CuratedMemoryWriteResult.builder()
                .success(false)
                .message(message)
                .usedChars(used)
                .limitChars(limit)
                .build();
    }

    public static CuratedMemoryWriteResult staged(String message, int used, int limit) {
        return CuratedMemoryWriteResult.builder()
                .success(true)
                .staged(true)
                .message(message)
                .usedChars(used)
                .limitChars(limit)
                .build();
    }
}
