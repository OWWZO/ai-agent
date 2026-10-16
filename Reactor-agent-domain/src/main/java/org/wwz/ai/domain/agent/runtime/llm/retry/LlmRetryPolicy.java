package org.wwz.ai.domain.agent.runtime.llm.retry;

/**
 * LLM 重试策略（次数 / 退避曲线 / jitter），全部可由环境变量或系统属性覆盖。
 *
 * <p>移植自 ZCode 的 {@code retry-policy.ts}（含 {@code ResolvedAiSdkModelRetryOptions}），
 * 默认值与 ZCode 对齐：10 次重试（共 11 次尝试）/ 2s 起步 / ×2 退避 / 60s 封顶 / 开启 jitter。
 * 注意 {@code maxRetries} 语义与 ZCode 一致：指的是「额外重试次数」，总尝试数 = maxRetries + 1。
 *
 * @param maxAttempts   总尝试次数（含首次），必须 &gt;= 1
 * @param baseDelayMs   首次退避基准
 * @param backoffFactor 退避倍数
 * @param maxDelayMs    退避上限
 * @param jitter        是否加抖动（避免多请求同时重试造成尖峰）
 */
public record LlmRetryPolicy(int maxAttempts,
                             long baseDelayMs,
                             double backoffFactor,
                             long maxDelayMs,
                             boolean jitter) {

    /** 额外重试次数（与 ZCode 的 DEFAULT_MAX_RETRIES 对齐）。 */
    public static final int DEFAULT_MAX_RETRIES = 10;
    public static final long DEFAULT_BASE_DELAY_MS = 2_000L;
    public static final double DEFAULT_BACKOFF_FACTOR = 2d;
    public static final long DEFAULT_MAX_DELAY_MS = 60_000L;

    public static final String ENV_MAX_RETRIES = "REACTOR_LLM_RETRY_MAX_RETRIES";
    public static final String ENV_BASE_DELAY_MS = "REACTOR_LLM_RETRY_BASE_DELAY_MS";
    public static final String ENV_BACKOFF_FACTOR = "REACTOR_LLM_RETRY_BACKOFF_FACTOR";
    public static final String ENV_MAX_DELAY_MS = "REACTOR_LLM_RETRY_MAX_DELAY_MS";
    public static final String ENV_JITTER = "REACTOR_LLM_RETRY_JITTER";

    public LlmRetryPolicy {
        if (maxAttempts < 1) {
            maxAttempts = 1;
        }
        if (baseDelayMs < 0) {
            baseDelayMs = 0;
        }
        if (!(backoffFactor > 0)) {
            backoffFactor = DEFAULT_BACKOFF_FACTOR;
        }
        if (maxDelayMs < 0) {
            maxDelayMs = 0;
        }
    }

    /** 额外重试次数（总尝试 - 1）。 */
    public int maxRetries() {
        return Math.max(0, maxAttempts - 1);
    }

    public static LlmRetryPolicy defaults() {
        return new LlmRetryPolicy(
                DEFAULT_MAX_RETRIES + 1,
                DEFAULT_BASE_DELAY_MS,
                DEFAULT_BACKOFF_FACTOR,
                DEFAULT_MAX_DELAY_MS,
                true);
    }

    /** 读取环境变量/系统属性覆盖；未配置项沿用默认值。 */
    public static LlmRetryPolicy fromEnvironment() {
        LlmRetryPolicy base = defaults();
        Long maxRetries = readLongSetting(ENV_MAX_RETRIES);
        Long baseDelay = readLongSetting(ENV_BASE_DELAY_MS);
        Double factor = readDoubleSetting(ENV_BACKOFF_FACTOR);
        Long maxDelay = readLongSetting(ENV_MAX_DELAY_MS);
        Boolean jitter = readBooleanSetting(ENV_JITTER);
        return new LlmRetryPolicy(
                maxRetries == null ? base.maxAttempts() : (int) Math.max(1, maxRetries + 1),
                baseDelay == null ? base.baseDelayMs() : Math.max(0, baseDelay),
                factor == null ? base.backoffFactor() : factor,
                maxDelay == null ? base.maxDelayMs() : Math.max(0, maxDelay),
                jitter == null ? base.jitter() : jitter);
    }

    public LlmRetryPolicy withMaxAttempts(int attempts) {
        return new LlmRetryPolicy(attempts, baseDelayMs, backoffFactor, maxDelayMs, jitter);
    }

    public LlmRetryPolicy withMaxRetries(int retries) {
        return withMaxAttempts(Math.max(1, retries + 1));
    }

    /** 环境变量优先，其次同名系统属性。 */
    static String readSetting(String key) {
        String fromEnv = System.getenv(key);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        String fromProperty = System.getProperty(key);
        return fromProperty == null || fromProperty.isBlank() ? null : fromProperty.trim();
    }

    static Long readLongSetting(String key) {
        String raw = readSetting(key);
        if (raw == null) {
            return null;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ignore) {
            return null;
        }
    }

    static Double readDoubleSetting(String key) {
        String raw = readSetting(key);
        if (raw == null) {
            return null;
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ignore) {
            return null;
        }
    }

    static Boolean readBooleanSetting(String key) {
        String raw = readSetting(key);
        if (raw == null) {
            return null;
        }
        String normalized = raw.toLowerCase(java.util.Locale.ROOT);
        if ("true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized)) {
            return Boolean.TRUE;
        }
        if ("false".equals(normalized) || "0".equals(normalized) || "no".equals(normalized)) {
            return Boolean.FALSE;
        }
        return null;
    }
}
