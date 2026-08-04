package org.wwz.ai.domain.agent.memory.ltm;

/**
 * 运行时 LTM 服务定位（避免 ReactorRuntimeDependencies 构建时 ObjectProvider 快照为 null）。
 */
public final class LtmServices {

    private static volatile BackgroundReviewService backgroundReviewService;
    private static volatile MemoryFlushService memoryFlushService;

    private LtmServices() {
    }

    public static void bind(BackgroundReviewService review, MemoryFlushService flush) {
        backgroundReviewService = review;
        memoryFlushService = flush;
    }

    public static BackgroundReviewService backgroundReview() {
        return backgroundReviewService;
    }

    public static MemoryFlushService memoryFlush() {
        return memoryFlushService;
    }
}
