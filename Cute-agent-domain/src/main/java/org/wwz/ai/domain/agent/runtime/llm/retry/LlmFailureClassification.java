package org.wwz.ai.domain.agent.runtime.llm.retry;

/**
 * 一次模型请求失败的结构化分类结果。
 *
 * <p>移植自 ZCode 的 {@code ClassifiedModelFailure}。这是整个重试链路唯一的失败事实来源：
 * 重试闸门读 {@link #isRetryable()}，退避计算读 {@link #retryAfterMs()}，
 * workflow 策略表读 {@link #code()} / {@link #reason()} / {@link #providerCode()}。
 *
 * @param code            稳定错误码
 * @param message         面向日志/用户的说明（provider 业务错误时保留原文）
 * @param reason          归因
 * @param retryable       分类器给出的可重试判定（取消除外）
 * @param retryAfterMs    provider 通过 Retry-After 头给出的建议等待毫秒数，可为 null
 * @param statusCode      HTTP 状态码，可为 null
 * @param providerCode    provider 业务码（如 BigModel 的 1005/3001），可为 null
 * @param providerRequestId provider 侧请求 id，便于对账，可为 null
 */
public record LlmFailureClassification(
        LlmErrorCode code,
        String message,
        LlmFailureReason reason,
        boolean retryable,
        Long retryAfterMs,
        Integer statusCode,
        String providerCode,
        String providerRequestId) {

    public LlmFailureClassification {
        if (code == null) {
            code = LlmErrorCode.MODEL_REQUEST_FAILED;
        }
        if (reason == null) {
            reason = LlmFailureReason.UNKNOWN;
        }
        if (message == null || message.isBlank()) {
            message = "Model request failed.";
        }
    }

    /**
     * 重试闸门读这个，而不是直接读 {@link #retryable()}：
     * 取消永远不重试，哪怕上游把它标成可重试。
     */
    public boolean isRetryable() {
        return retryable && reason != LlmFailureReason.CANCELLED;
    }

    public boolean isCancelled() {
        return reason == LlmFailureReason.CANCELLED;
    }

    /** provider 给了 Retry-After 才有值，供退避计算与 workflow 的配额重置时刻使用。 */
    public boolean hasRetryAfter() {
        return retryAfterMs != null && retryAfterMs >= 0;
    }

    public LlmFailureClassification withMessage(String newMessage) {
        return new LlmFailureClassification(
                code, newMessage, reason, retryable, retryAfterMs, statusCode, providerCode, providerRequestId);
    }

    public LlmFailureClassification withProviderCode(String newProviderCode) {
        return new LlmFailureClassification(
                code, message, reason, retryable, retryAfterMs, statusCode, newProviderCode, providerRequestId);
    }

    public LlmFailureClassification withRetryAfterMs(Long newRetryAfterMs) {
        return new LlmFailureClassification(
                code, message, reason, retryable, newRetryAfterMs, statusCode, providerCode, providerRequestId);
    }

    public static LlmFailureClassification of(LlmErrorCode code,
                                              String message,
                                              LlmFailureReason reason,
                                              boolean retryable) {
        return new LlmFailureClassification(code, message, reason, retryable, null, null, null, null);
    }

    public static LlmFailureClassification of(LlmErrorCode code,
                                              String message,
                                              LlmFailureReason reason,
                                              boolean retryable,
                                              Long retryAfterMs,
                                              Integer statusCode) {
        return new LlmFailureClassification(code, message, reason, retryable, retryAfterMs, statusCode, null, null);
    }

    public static LlmFailureClassification cancelled() {
        return of(LlmErrorCode.MODEL_REQUEST_CANCELLED,
                "Model request was cancelled.",
                LlmFailureReason.CANCELLED,
                false);
    }
}
