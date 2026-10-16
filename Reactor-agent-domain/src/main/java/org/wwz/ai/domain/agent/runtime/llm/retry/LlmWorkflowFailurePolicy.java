package org.wwz.ai.domain.agent.runtime.llm.retry;

import java.util.Set;

/**
 * workflow / 子代理场景的模型失败策略表。
 *
 * <p>移植自 ZCode 的 {@code workflow-model-failure-policy.ts}。
 *
 * <p>与 {@link LlmFailureClassifier} 的分工：分类器回答「主对话要不要自动重试」，
 * 这张表回答「后台任务要不要停下来找人」。两者在配额/并发码上答案相反，这正是本类存在的理由：
 * 后台流量持有 {@link LlmRetryBudget#UNBOUNDED} 预算时，只有<b>确定性的、需要人来解决的</b>失败
 * 才值得停下任务；其余（含分类器判不可重试的未知业务码、TLS、5xx）一律在内重试。
 */
public final class LlmWorkflowFailurePolicy {

    private LlmWorkflowFailurePolicy() {
    }

    public enum Decision {
        /** 继续重试。 */
        RETRY,
        /** 停下任务，等用户处理。 */
        STOP,
        /** 上下文超限：交由上层压缩后重跑。 */
        CONTEXT_EXCEEDED,
        /** 取消。 */
        CANCELLED
    }

    /** {@link Decision#STOP} 的原因分类，用于选择提示文案。 */
    public enum StopKind {
        AUTH,
        NOT_CONFIGURED,
        MODEL_UNAVAILABLE,
        INVALID_REQUEST,
        QUOTA,
        OTHER
    }

    public record Policy(Decision decision, StopKind kind) {

        public static Policy retry() {
            return new Policy(Decision.RETRY, null);
        }

        public static Policy stop(StopKind kind) {
            return new Policy(Decision.STOP, kind);
        }

        public static Policy contextExceeded() {
            return new Policy(Decision.CONTEXT_EXCEEDED, null);
        }

        public static Policy cancelled() {
            return new Policy(Decision.CANCELLED, null);
        }
    }

    /**
     * 配额类业务码。与 {@link LlmProviderBusinessCodes} 的终止型限流映射<b>有意分开维护</b>：
     * 那张表决定主对话不自动重试，这张表决定后台任务停下来等配额重置/充值。
     */
    public static final Set<String> QUOTA_PROVIDER_CODES = Set.of(
            "1005", "1308", "1310", "1313", "1316", "1317", "1318", "1319", "1320", "1321",
            "2056", "20097",
            "insufficient_quota", "credit_balance_exhausted",
            "organization_spend_limit_exceeded", "project_spend_limit_exceeded",
            "organization_usage_limit_exceeded", "exceeded_current_quota_error");

    /**
     * 「provider 没配好」一族的错误码。注意不要把 {@link LlmErrorCode#INVALID_MODEL_REQUEST} 放进来：
     * 那是 provider 对请求的拒绝，走 INVALID_REQUEST 分支；也不要把 MODEL_NOT_FOUND 放进来，
     * 它有独立的 model_unavailable 语义。
     */
    private static final Set<LlmErrorCode> NOT_CONFIGURED_ERROR_CODES = Set.of(
            LlmErrorCode.PROVIDER_NOT_CONFIGURED);

    /**
     * 策略表本体。顺序有意义：取消最先（它不是错误）；配额按<b>码</b>判，先于 reason
     * （1005 的 reason 是 INVALID_REQUEST）；其余按 reason / code；表外一切 = 重试。
     */
    public static Policy resolve(LlmFailureClassification failure, String providerCode) {
        if (failure.reason() == LlmFailureReason.CANCELLED) {
            return Policy.cancelled();
        }
        String normalizedCode = LlmProviderBusinessCodes.normalizeCode(providerCode);
        if (normalizedCode != null && QUOTA_PROVIDER_CODES.contains(normalizedCode)) {
            return Policy.stop(StopKind.QUOTA);
        }
        if (failure.reason() == LlmFailureReason.AUTH_FAILED) {
            return Policy.stop(StopKind.AUTH);
        }
        if (failure.reason() == LlmFailureReason.PROVIDER_NOT_CONFIGURED
                || NOT_CONFIGURED_ERROR_CODES.contains(failure.code())) {
            return Policy.stop(StopKind.NOT_CONFIGURED);
        }
        if (failure.code() == LlmErrorCode.MODEL_NOT_FOUND) {
            return Policy.stop(StopKind.MODEL_UNAVAILABLE);
        }
        if (failure.reason() == LlmFailureReason.CONTEXT_EXCEEDED) {
            return Policy.contextExceeded();
        }
        // 「请求无效」只认 provider 对请求的拒绝。INVALID_MODEL_RESPONSE 是响应解析失败，
        // 再问一次很可能就好，归重试。
        if (failure.reason() == LlmFailureReason.INVALID_REQUEST
                && failure.code() != LlmErrorCode.INVALID_MODEL_RESPONSE) {
            return Policy.stop(StopKind.INVALID_REQUEST);
        }
        return Policy.retry();
    }

    /**
     * 重试闸门的预算感知版本：有界预算照旧读分类器的可重试判定（主对话行为不变）；
     * 无上限预算（后台流量）改读策略表。
     */
    public static boolean retryAllowed(LlmFailureClassification failure,
                                       LlmRetryBudget budget,
                                       String providerCode) {
        if (!LlmRetryBudget.isUnbounded(budget)) {
            return failure.isRetryable();
        }
        return resolve(failure, providerCode).decision() == Decision.RETRY;
    }
}
