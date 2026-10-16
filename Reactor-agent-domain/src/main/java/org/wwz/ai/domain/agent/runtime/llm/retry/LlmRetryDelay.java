package org.wwz.ai.domain.agent.runtime.llm.retry;

/**
 * 退避时长计算。
 *
 * <p>移植自 ZCode 的 {@code calculateRetryDelay}。三条规则：
 * <ol>
 *   <li>指数退避：{@code base × factor^(attempt-1)}，封顶 maxDelay</li>
 *   <li><b>Retry-After 优先</b>：provider 明确给了等待时长且该时长合理时，直接用它——
 *       限流场景的合法等待常常是几十秒到几分钟，被 maxDelay 压成几秒等于白白多打几次</li>
 *   <li>jitter：在封顶值上乘 0.5–1.0 的随机系数，打散多请求同时重试的尖峰</li>
 * </ol>
 */
public final class LlmRetryDelay {

    /** 超过这个时长的 Retry-After 视为不可信（除非比本地退避还短）。 */
    public static final long MAX_REASONABLE_RETRY_AFTER_MS = 5 * 60_000L;

    private LlmRetryDelay() {
    }

    /**
     * @param attempt      1-based 尝试序号（即将进行的第几次）
     * @param retryAfterMs provider 给出的建议等待，可为 null
     */
    public static long calculate(LlmRetryPolicy policy, int attempt, Long retryAfterMs) {
        double uncapped = policy.baseDelayMs() * Math.pow(policy.backoffFactor(), Math.max(0, attempt - 1));
        long capped = (long) Math.min(uncapped, policy.maxDelayMs());

        if (isReasonableRetryAfterMs(retryAfterMs, uncapped)) {
            return retryAfterMs;
        }
        if (!policy.jitter() || capped == 0) {
            return capped;
        }
        return Math.round(capped * (0.5d + Math.random() * 0.5d));
    }

    public static long calculate(LlmRetryPolicy policy, int attempt) {
        return calculate(policy, attempt, null);
    }

    /**
     * Retry-After 可信条件：非负且（不超过 5 分钟，或比本地指数退避还短）。
     * 后者用于「provider 给了个很小的 Retry-After，本地退避反而更长」的场景。
     */
    private static boolean isReasonableRetryAfterMs(Long value, double exponentialDelayMs) {
        return value != null
                && value >= 0
                && (value <= MAX_REASONABLE_RETRY_AFTER_MS || value < exponentialDelayMs);
    }
}
