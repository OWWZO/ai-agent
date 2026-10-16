package org.wwz.ai.domain.agent.runtime.llm.retry;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * provider 业务码 → 分类结果的映射表。
 *
 * <p>移植自 ZCode 的 {@code failure-provider-business-codes.ts}。存在的理由：
 * 同一批上游（BigModel / OpenAI 兼容中转）会用 200/4xx 携带业务码表达
 * 「配额耗尽」「并发上限」「上下文超限」等语义，这些语义无法从 HTTP 状态码反推，
 * 不显式入表就会落进通用 429/400 分支被错误重试或错误终止。
 *
 * <p>维护约定：只消费 provider 适配层已解析出的业务码，不在这里解析厂商原始响应字段。
 */
public final class LlmProviderBusinessCodes {

    private LlmProviderBusinessCodes() {
    }

    /** 分类模板：业务码命中后直接套用。 */
    public record Mapping(LlmErrorCode code, String message, LlmFailureReason reason, boolean retryable) {
    }

    private static final Mapping TERMINAL_RATE_LIMIT = new Mapping(
            LlmErrorCode.MODEL_RATE_LIMITED, null, LlmFailureReason.RATE_LIMITED, false);
    private static final Mapping RETRYABLE_RATE_LIMIT = new Mapping(
            LlmErrorCode.MODEL_RATE_LIMITED, null, LlmFailureReason.RATE_LIMITED, true);
    private static final Mapping RETRYABLE_OVERLOAD = new Mapping(
            LlmErrorCode.MODEL_REQUEST_FAILED, null, LlmFailureReason.PROVIDER_OVERLOADED, true);
    private static final Mapping TERMINAL_BUSINESS = new Mapping(
            LlmErrorCode.MODEL_REQUEST_FAILED, null, LlmFailureReason.UNKNOWN, false);

    private static final Map<String, Mapping> MAPPINGS = buildMappings();

    /** 被包装成业务码的可恢复上游网络故障。 */
    private static final Set<String> PROVIDER_NETWORK_BUSINESS_CODES = Set.of("1234");

    private static final Set<String> PROVIDER_INTERNAL_NETWORK_MESSAGES = Set.of(
            "500 internal network error", "internal network error", "internal network failure");

    private static Map<String, Mapping> buildMappings() {
        Map<String, Mapping> map = new LinkedHashMap<>();
        map.put("500", new Mapping(LlmErrorCode.MODEL_REQUEST_FAILED, null, LlmFailureReason.SERVER_ERROR, true));
        map.put("1006", new Mapping(LlmErrorCode.PROVIDER_NOT_CONFIGURED, null, LlmFailureReason.AUTH_FAILED, false));
        map.put("1005", new Mapping(LlmErrorCode.MODEL_REQUEST_FAILED, null, LlmFailureReason.INVALID_REQUEST, false));
        map.put("3006", new Mapping(LlmErrorCode.MODEL_NOT_FOUND, null, LlmFailureReason.INVALID_REQUEST, false));
        map.put("3001", new Mapping(LlmErrorCode.INVALID_MODEL_REQUEST, null, LlmFailureReason.INVALID_REQUEST, false));
        map.put("3007", new Mapping(LlmErrorCode.INVALID_MODEL_REQUEST, null, LlmFailureReason.AUTH_FAILED, false));
        // 3008/3009/3010：并发上限。不自动重试，由上层引导换模型或升级套餐。
        map.put("3008", TERMINAL_RATE_LIMIT);
        map.put("3009", TERMINAL_RATE_LIMIT);
        map.put("3010", TERMINAL_RATE_LIMIT);
        map.put("1120", new Mapping(LlmErrorCode.MODEL_REQUEST_FAILED, null, LlmFailureReason.SERVER_ERROR, true));
        map.put("1230", new Mapping(LlmErrorCode.MODEL_REQUEST_FAILED, null, LlmFailureReason.SERVER_ERROR, true));
        map.put("1234", new Mapping(LlmErrorCode.MODEL_REQUEST_FAILED, null, LlmFailureReason.NETWORK_ERROR, true));
        map.put("1261", new Mapping(LlmErrorCode.MODEL_CONTEXT_EXCEEDED, null, LlmFailureReason.CONTEXT_EXCEEDED, false));
        map.put("1113", TERMINAL_BUSINESS);
        map.put("1302", RETRYABLE_RATE_LIMIT);
        map.put("1303", RETRYABLE_RATE_LIMIT);
        map.put("1305", RETRYABLE_RATE_LIMIT);
        map.put("1304", TERMINAL_RATE_LIMIT);
        map.put("1308", TERMINAL_RATE_LIMIT);
        map.put("1309", TERMINAL_BUSINESS);
        map.put("1310", TERMINAL_RATE_LIMIT);
        map.put("1311", TERMINAL_BUSINESS);
        map.put("1312", RETRYABLE_OVERLOAD);
        map.put("1313", TERMINAL_RATE_LIMIT);
        map.put("3002", RETRYABLE_RATE_LIMIT);
        map.put("2007", new Mapping(LlmErrorCode.MODEL_REQUEST_FAILED, null, LlmFailureReason.SERVER_ERROR, true));

        // 需要用户充值 / 调整套餐 / 等待长期额度重置：终止语义，不能靠 Retry-After 时长兜底成重试。
        for (String code : new String[]{
                "insufficient_quota", "credit_balance_exhausted", "organization_spend_limit_exceeded",
                "project_spend_limit_exceeded", "organization_usage_limit_exceeded",
                "exceeded_current_quota_error", "2056", "20097", "1316", "1317", "1318", "1319", "1320", "1321"}) {
            map.put(code, TERMINAL_RATE_LIMIT);
        }
        for (String code : new String[]{"1008", "1314", "1315"}) {
            map.put(code, TERMINAL_BUSINESS);
        }
        for (String code : new String[]{"rate_limit_reached_error", "rate_limit_error"}) {
            map.put(code, RETRYABLE_RATE_LIMIT);
        }
        for (String code : new String[]{"engine_overloaded_error", "overloaded_error"}) {
            map.put(code, RETRYABLE_OVERLOAD);
        }
        return Map.copyOf(map);
    }

    public static Mapping lookup(String providerCode) {
        String normalized = normalizeCode(providerCode);
        return normalized == null ? null : MAPPINGS.get(normalized);
    }

    /** 数字码统一成去尾零的字符串；包装码 PROVIDER_BUSINESS_ERROR 不算真实业务码。 */
    public static String normalizeCode(String providerCode) {
        if (providerCode == null) {
            return null;
        }
        String trimmed = providerCode.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (LlmProviderBusinessException.CODE.equalsIgnoreCase(trimmed)) {
            return null;
        }
        return trimmed;
    }

    /**
     * 业务码本身表达的是传输层网络故障（如 SSE error chunk 把 ECONNRESET 塞进 providerCode）。
     * 这类失败必须沿用网络重试语义，不能落成 unknown 终止。
     */
    public static boolean isRetryableProviderBusinessNetworkFailure(LlmProviderBusinessException error,
                                                                    String providerCode) {
        String normalized = normalizeCode(providerCode);
        if (normalized != null) {
            if (LlmFailureInspector.isNetworkFailure(normalized)) {
                return true;
            }
            if (PROVIDER_NETWORK_BUSINESS_CODES.contains(normalized)) {
                return true;
            }
            String lower = normalized.toLowerCase(Locale.ROOT);
            if ("network_error".equals(lower) || "network_error_retryable".equals(lower)) {
                return true;
            }
        }
        return isInternalNetworkFailure(error);
    }

    private static boolean isInternalNetworkFailure(LlmProviderBusinessException error) {
        String message = normalizeMessage(error.getMessage());
        if (message == null) {
            return false;
        }
        return PROVIDER_INTERNAL_NETWORK_MESSAGES.contains(message);
    }

    private static String normalizeMessage(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim().toLowerCase(Locale.ROOT);
        return text.isEmpty() ? null : text;
    }

    /** 业务码本身是超时（headers/body timeout 被塞进 providerCode）时保留超时可重试语义。 */
    public static boolean isRetryableProviderBusinessTimeoutFailure(LlmProviderBusinessException error,
                                                                     String providerCode,
                                                                     Integer statusCode) {
        return LlmFailureInspector.isTimeoutFailure(
                error,
                providerCode,
                statusCode != null ? statusCode : error.getStatusCode());
    }
}
