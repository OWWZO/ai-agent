package org.wwz.ai.domain.agent.memory.ltm;

import lombok.Builder;
import lombok.Value;

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
