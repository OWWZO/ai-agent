package org.wwz.ai.domain.agent.runtime.llm.retry;

/**
 * 空补全（空 completion）重试。
 *
 * <p>移植自 ZCode 的 {@code empty-completion-retry.ts}。场景：provider 返回 200 但既没有正文、
 * 也没有 tool call、也没有 usage —— 上游偶发抖动，重发一次通常就有内容。
 *
 * <p>约束：独立于主重试预算的<b>单次</b>上限（{@link #MAX_RETRIES}），避免把「模型真的返回空」
 * 拖成无限重试；且不得在用户已取消时重试。
 */
public final class LlmEmptyCompletion {

    /** 空补全最多额外重试 1 次。 */
    public static final int MAX_RETRIES = 1;

    public static final String MESSAGE =
            "Model returned no text, no tool calls, and no usage before completing the turn.";

    private LlmEmptyCompletion() {
    }

    /** 空补全的分类结果：可重试，归因为未知（上游没给任何证据）。 */
    public static LlmFailureClassification failure() {
        return LlmFailureClassification.of(
                LlmErrorCode.INVALID_MODEL_RESPONSE,
                MESSAGE,
                LlmFailureReason.UNKNOWN,
                true);
    }

    /**
     * @param aborted    用户/任务是否已取消
     * @param attempt    当前 attempt（1-based）
     * @param maxAttempts 主重试的总尝试上限
     * @param retryCount 本请求已发生的空补全重试次数
     */
    public static boolean canRetry(boolean aborted, int attempt, int maxAttempts, int retryCount) {
        return !aborted
                && retryCount < MAX_RETRIES
                && attempt < maxAttempts;
    }
}
