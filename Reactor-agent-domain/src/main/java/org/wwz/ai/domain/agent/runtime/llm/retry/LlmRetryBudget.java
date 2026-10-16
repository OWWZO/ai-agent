package org.wwz.ai.domain.agent.runtime.llm.retry;

/**
 * 重试预算档位。
 *
 * <p>移植自 ZCode 的 {@code retry-budget.ts}。存在理由：主对话需要「有限次重试后把错误交给用户」，
 * 而后台 workflow / 子代理希望「只要上游还能恢复就继续熬」。
 *
 * <p>语义边界（与 ZCode 一致，改动前请确认）：「无上限」<b>只</b>放宽瞬态失败的放弃条件，
 * 退避曲线（含 Retry-After 优先与 jitter）、{@link LlmFailureClassification#isRetryable()} 的判定、
 * 空补全重试的独立上限一律不受影响。
 */
public enum LlmRetryBudget {

    /** 有限预算：按策略的 maxAttempts 停止。 */
    BOUNDED,
    /** 无上限预算：瞬态失败不因次数耗尽而放弃。 */
    UNBOUNDED;

    /** 状态事件/日志里表示「无上限」的哨兵（0 不占用合法计数）。 */
    public static final int UNBOUNDED_MAX_ATTEMPTS_SENTINEL = 0;

    public boolean isUnbounded() {
        return this == UNBOUNDED;
    }

    /** 失败之后还允许再试一次吗。 */
    public boolean allows(int retryBudgetAttempt, int maxAttempts) {
        return isUnbounded() || retryBudgetAttempt < maxAttempts;
    }

    /** attempt 循环的继续条件。 */
    public boolean loopContinues(int attempt, int loopMaxAttempts) {
        return isUnbounded() || attempt <= loopMaxAttempts;
    }

    /** 写进状态事件 / 日志的 maxAttempts：unbounded 下是哨兵 0。 */
    public int statusMaxAttempts(int maxAttempts) {
        return isUnbounded() ? UNBOUNDED_MAX_ATTEMPTS_SENTINEL : maxAttempts;
    }

    /** null 视为 BOUNDED：调用方不显式传预算时行为与旧实现一致。 */
    public static boolean isUnbounded(LlmRetryBudget budget) {
        return budget == UNBOUNDED;
    }

    public static boolean allows(LlmRetryBudget budget, int retryBudgetAttempt, int maxAttempts) {
        return budget == UNBOUNDED || retryBudgetAttempt < maxAttempts;
    }

    public static boolean loopContinues(LlmRetryBudget budget, int attempt, int loopMaxAttempts) {
        return budget == UNBOUNDED || attempt <= loopMaxAttempts;
    }

    public static int statusMaxAttempts(LlmRetryBudget budget, int maxAttempts) {
        return budget == UNBOUNDED ? UNBOUNDED_MAX_ATTEMPTS_SENTINEL : maxAttempts;
    }
}
