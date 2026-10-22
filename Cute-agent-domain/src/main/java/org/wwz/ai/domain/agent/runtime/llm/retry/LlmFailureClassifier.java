package org.wwz.ai.domain.agent.runtime.llm.retry;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 模型请求失败分类器。
 *
 * <p>移植自 ZCode 的 {@code failure-classifier.ts}。判定顺序有意义，改动前先读注释：
 * <ol>
 *   <li>本域显式标记的响应/请求非法码</li>
 *   <li>取消（它不是错误，优先级最高）</li>
 *   <li>provider 业务码（配额/并发/超窗语义无法从状态码反推）</li>
 *   <li>超时 → 429 → 529 → 401/403 → 400/422 → 超窗 → 5xx</li>
 *   <li>TLS（证书失败终止；握手中断可重试）→ 代理 → 网络</li>
 *   <li>provider 显式标记的可重试</li>
 *   <li>兼容兜底：旧的英文文案匹配（仅为不改变既有行为而保留，见类尾注释）</li>
 * </ol>
 */
public final class LlmFailureClassifier {

    private LlmFailureClassifier() {
    }

    /**
     * 兼容兜底层：旧 {@code LlmRequestRetry} 依赖的英文文案匹配。
     *
     * <p>保留原因：上游异常类型层次不稳定时（例如业务侧把网络异常包成
     * {@code RuntimeException("Upstream request failed")}），类型与状态码都读不到，
     * 只能靠文案兜底。结构化判定命中时永远不会走到这里。
     */
    private static final String[] LEGACY_TRANSIENT_MARKERS = {
            "upstream request failed",
            "temporarily unavailable",
            "service unavailable",
            "gateway timeout",
            "bad gateway",
            "timeout",
            "timed out",
            "connection reset",
            "connection aborted",
            "connection error",
            "remote end closed",
            "broken pipe",
            "too many requests",
            "rate limit",
            "overloaded",
            "server error",
            "internal server error",
            "try again",
            "temporar",
            "eof",
            "unexpected_eof",
            "unexpected end-of-input",
            "ssl",
            "tls",
            "handshake",
            "i/o error",
            "read timed out",
            "connect timed out",
            "json parse error",
            "json eof",
            "message not readable",
            "empty response",
            "empty body",
            "response body is empty",
            "premature close",
            "prematureclose",
            "response ended prematurely",
            "incomplete response",
            "connection closed",
            "protocol error"
    };

    /** 兼容兜底：旧实现按瞬态处理的状态码（含 Cloudflare 520–527，524 为源站超时）。 */
    private static final Set<Integer> LEGACY_TRANSIENT_STATUS_CODES = Set.of(
            408, 409, 425, 429, 500, 502, 503, 504, 520, 521, 522, 523, 524, 525, 527);

    private static final String CODE_INVALID_MODEL_RESPONSE = "INVALID_MODEL_RESPONSE";
    private static final String CODE_INVALID_MODEL_REQUEST = "INVALID_MODEL_REQUEST";

    public static LlmFailureClassification classify(Throwable error) {
        return classify(error, false);
    }

    /**
     * @param aborted 调用方已知的取消状态（例如 Agent run 已 stop）；为 true 时一律归为取消
     */
    public static LlmFailureClassification classify(Throwable error, boolean aborted) {
        Throwable unwrapped = LlmFailureInspector.unwrap(error);
        Integer statusCode = LlmFailureInspector.statusCode(unwrapped);
        String code = LlmFailureInspector.errorCode(unwrapped);
        Map<String, String> headers = LlmFailureInspector.responseHeaders(unwrapped);
        Long retryAfterMs = LlmFailureInspector.parseRetryAfterMs(headers);

        if (CODE_INVALID_MODEL_RESPONSE.equalsIgnoreCase(code)) {
            return LlmFailureClassification.of(
                    LlmErrorCode.INVALID_MODEL_RESPONSE,
                    messageOf(unwrapped, "Model returned an invalid response."),
                    LlmFailureReason.INVALID_REQUEST,
                    false);
        }
        if (CODE_INVALID_MODEL_REQUEST.equalsIgnoreCase(code)) {
            return LlmFailureClassification.of(
                    LlmErrorCode.INVALID_MODEL_REQUEST,
                    messageOf(unwrapped, "Model request configuration is invalid."),
                    LlmFailureReason.INVALID_REQUEST,
                    false);
        }

        if (LlmFailureInspector.isAbortFailure(unwrapped, aborted)) {
            return LlmFailureClassification.cancelled();
        }

        LlmProviderBusinessException business = findProviderBusinessError(unwrapped);
        if (business != null) {
            return classifyProviderBusinessFailure(business, statusCode, retryAfterMs);
        }

        if (LlmFailureInspector.isTimeoutFailure(unwrapped, code, statusCode)) {
            return LlmFailureClassification.of(
                    LlmErrorCode.MODEL_REQUEST_TIMEOUT,
                    "Model request timed out.",
                    LlmFailureReason.TIMEOUT,
                    true,
                    retryAfterMs,
                    statusCode);
        }

        if (statusCode != null && statusCode == 429) {
            return LlmFailureClassification.of(
                    LlmErrorCode.MODEL_RATE_LIMITED,
                    "Provider rate limited the model request.",
                    LlmFailureReason.RATE_LIMITED,
                    true,
                    retryAfterMs,
                    statusCode);
        }

        if (statusCode != null && statusCode == 529) {
            return LlmFailureClassification.of(
                    LlmErrorCode.MODEL_REQUEST_FAILED,
                    "Provider is overloaded.",
                    LlmFailureReason.PROVIDER_OVERLOADED,
                    true,
                    retryAfterMs,
                    statusCode);
        }

        if (statusCode != null && (statusCode == 401 || statusCode == 403)) {
            return LlmFailureClassification.of(
                    LlmErrorCode.PROVIDER_NOT_CONFIGURED,
                    "Provider authentication failed.",
                    LlmFailureReason.AUTH_FAILED,
                    false,
                    null,
                    statusCode);
        }

        if (statusCode != null && (statusCode == 400 || statusCode == 422)) {
            boolean contextExceeded = LlmFailureInspector.isContextExceededFailure(unwrapped);
            return LlmFailureClassification.of(
                    contextExceeded ? LlmErrorCode.MODEL_CONTEXT_EXCEEDED : LlmErrorCode.INVALID_MODEL_REQUEST,
                    contextExceeded
                            ? "Model request exceeded the provider context window."
                            : "Provider rejected the model request.",
                    contextExceeded ? LlmFailureReason.CONTEXT_EXCEEDED : LlmFailureReason.INVALID_REQUEST,
                    false,
                    null,
                    statusCode);
        }

        if (LlmFailureInspector.isContextExceededFailure(unwrapped)) {
            return LlmFailureClassification.of(
                    LlmErrorCode.MODEL_CONTEXT_EXCEEDED,
                    "Model request exceeded the provider context window.",
                    LlmFailureReason.CONTEXT_EXCEEDED,
                    false,
                    null,
                    statusCode);
        }

        if (statusCode != null && statusCode >= 500) {
            return LlmFailureClassification.of(
                    LlmErrorCode.MODEL_REQUEST_FAILED,
                    "Provider returned a server error.",
                    LlmFailureReason.SERVER_ERROR,
                    true,
                    retryAfterMs,
                    statusCode);
        }

        // 证书校验失败是配置问题，重试永远不会成功；握手被打断是可恢复瞬态故障。
        if (LlmFailureInspector.isCertificateValidationFailure(unwrapped)
                || LlmFailureInspector.isTlsFailure(code)) {
            return LlmFailureClassification.of(
                    LlmErrorCode.MODEL_REQUEST_FAILED,
                    "TLS validation failed for the provider request.",
                    LlmFailureReason.TLS_ERROR,
                    false,
                    null,
                    statusCode);
        }

        if (LlmFailureInspector.isTlsHandshakeInterrupt(unwrapped)) {
            return LlmFailureClassification.of(
                    LlmErrorCode.MODEL_REQUEST_FAILED,
                    "TLS handshake was interrupted for the provider request.",
                    LlmFailureReason.NETWORK_ERROR,
                    true,
                    retryAfterMs,
                    statusCode);
        }

        if (LlmFailureInspector.isProxyFailure(code) || LlmFailureInspector.isProxyThrowable(unwrapped)) {
            return LlmFailureClassification.of(
                    LlmErrorCode.MODEL_REQUEST_FAILED,
                    "Proxy connection failed for the provider request.",
                    LlmFailureReason.PROXY_ERROR,
                    true,
                    retryAfterMs,
                    statusCode);
        }

        if (LlmFailureInspector.isNetworkFailure(code) || LlmFailureInspector.isNetworkThrowable(unwrapped)) {
            return LlmFailureClassification.of(
                    LlmErrorCode.MODEL_REQUEST_FAILED,
                    "Network connection failed for the provider request.",
                    LlmFailureReason.NETWORK_ERROR,
                    true,
                    retryAfterMs,
                    statusCode);
        }

        if (LlmFailureInspector.isProviderMarkedRetryable(unwrapped)) {
            return LlmFailureClassification.of(
                    LlmErrorCode.MODEL_REQUEST_FAILED,
                    "Provider marked the model request as retryable.",
                    LlmFailureReason.SERVER_ERROR,
                    true,
                    retryAfterMs,
                    statusCode);
        }

        if (matchesLegacyTransient(unwrapped, statusCode)) {
            return LlmFailureClassification.of(
                    LlmErrorCode.MODEL_REQUEST_FAILED,
                    messageOf(unwrapped, "Model request failed."),
                    LlmFailureReason.UNKNOWN,
                    true,
                    retryAfterMs,
                    statusCode);
        }

        return LlmFailureClassification.of(
                LlmErrorCode.MODEL_REQUEST_FAILED,
                messageOf(unwrapped, "Model request failed."),
                LlmFailureReason.UNKNOWN,
                false,
                retryAfterMs,
                statusCode);
    }

    /** 兼容兜底判定：状态码命中旧瞬态集合，或文案命中旧瞬态 marker。 */
    private static boolean matchesLegacyTransient(Throwable error, Integer statusCode) {
        if (statusCode != null && LEGACY_TRANSIENT_STATUS_CODES.contains(statusCode)) {
            return true;
        }
        for (Throwable current : LlmFailureInspector.causeChain(error)) {
            String message = current.getMessage();
            if (message != null
                    && LlmFailureInspector.containsAny(message.toLowerCase(Locale.ROOT), LEGACY_TRANSIENT_MARKERS)) {
                return true;
            }
            String typeName = current.getClass().getSimpleName().toLowerCase(Locale.ROOT);
            if (typeName.contains("jsoneofexception")
                    || typeName.contains("eofexception")
                    || typeName.contains("prematureclose")
                    || typeName.contains("responseended")) {
                return true;
            }
        }
        return false;
    }

    /** 在因果链里找 provider 业务错误；AI SDK 式的二次包装会把真实业务码留在 cause 里。 */
    public static LlmProviderBusinessException findProviderBusinessError(Throwable error) {
        for (Throwable current : LlmFailureInspector.causeChain(LlmFailureInspector.unwrap(error))) {
            if (current instanceof LlmProviderBusinessException business) {
                return business;
            }
        }
        return null;
    }

    private static LlmFailureClassification classifyProviderBusinessFailure(LlmProviderBusinessException error,
                                                                            Integer statusCode,
                                                                            Long retryAfterMs) {
        String message = error.getMessage() == null || error.getMessage().isBlank()
                ? "Provider returned a business error."
                : error.getMessage();
        String providerCode = LlmProviderBusinessCodes.normalizeCode(error.getProviderCode());
        Integer effectiveStatus = statusCode != null ? statusCode : error.getStatusCode();

        LlmProviderBusinessCodes.Mapping mapping = providerCode == null
                ? null
                : LlmProviderBusinessCodes.lookup(providerCode);
        if (mapping != null) {
            return new LlmFailureClassification(
                    mapping.code(),
                    mapping.message() != null ? mapping.message() : message,
                    mapping.reason(),
                    mapping.retryable(),
                    retryAfterMs,
                    effectiveStatus,
                    providerCode,
                    error.getProviderRequestId());
        }

        if (LlmFailureInspector.isContextExceededFailure(error)) {
            return new LlmFailureClassification(
                    LlmErrorCode.MODEL_CONTEXT_EXCEEDED,
                    "Model request exceeded the provider context window.",
                    LlmFailureReason.CONTEXT_EXCEEDED,
                    false,
                    retryAfterMs,
                    effectiveStatus,
                    providerCode,
                    error.getProviderRequestId());
        }

        if (LlmProviderBusinessCodes.isRetryableProviderBusinessTimeoutFailure(error, providerCode, effectiveStatus)) {
            return new LlmFailureClassification(
                    LlmErrorCode.MODEL_REQUEST_TIMEOUT,
                    message,
                    LlmFailureReason.TIMEOUT,
                    true,
                    retryAfterMs,
                    effectiveStatus,
                    providerCode,
                    error.getProviderRequestId());
        }

        LlmFailureReason fallbackReason = effectiveStatus != null && effectiveStatus >= 500
                ? LlmFailureReason.SERVER_ERROR
                : LlmFailureReason.NETWORK_ERROR;

        if (effectiveStatus != null && (effectiveStatus == 401 || effectiveStatus == 403)) {
            return new LlmFailureClassification(
                    LlmErrorCode.PROVIDER_NOT_CONFIGURED, message, LlmFailureReason.AUTH_FAILED, false,
                    null, effectiveStatus, providerCode, error.getProviderRequestId());
        }
        if (effectiveStatus != null && effectiveStatus == 404) {
            return new LlmFailureClassification(
                    LlmErrorCode.MODEL_NOT_FOUND, message, LlmFailureReason.INVALID_REQUEST, false,
                    null, effectiveStatus, providerCode, error.getProviderRequestId());
        }
        if (effectiveStatus != null && (effectiveStatus == 400 || effectiveStatus == 422)) {
            return new LlmFailureClassification(
                    LlmErrorCode.INVALID_MODEL_REQUEST, message, LlmFailureReason.INVALID_REQUEST, false,
                    null, effectiveStatus, providerCode, error.getProviderRequestId());
        }
        if (effectiveStatus != null && effectiveStatus == 429) {
            return new LlmFailureClassification(
                    LlmErrorCode.MODEL_RATE_LIMITED, message, LlmFailureReason.RATE_LIMITED, true,
                    retryAfterMs, effectiveStatus, providerCode, error.getProviderRequestId());
        }
        if (LlmProviderBusinessCodes.isRetryableProviderBusinessNetworkFailure(error, providerCode)) {
            return new LlmFailureClassification(
                    LlmErrorCode.MODEL_REQUEST_FAILED, message, LlmFailureReason.NETWORK_ERROR, true,
                    retryAfterMs, effectiveStatus, providerCode, error.getProviderRequestId());
        }
        if (effectiveStatus != null && effectiveStatus >= 500) {
            return new LlmFailureClassification(
                    LlmErrorCode.MODEL_REQUEST_FAILED, message, LlmFailureReason.SERVER_ERROR, true,
                    retryAfterMs, effectiveStatus, providerCode, error.getProviderRequestId());
        }
        return new LlmFailureClassification(
                LlmErrorCode.MODEL_REQUEST_FAILED, message, LlmFailureReason.UNKNOWN, false,
                retryAfterMs, effectiveStatus, providerCode, error.getProviderRequestId());
    }

    private static String messageOf(Throwable error, String fallback) {
        String message = error == null ? null : error.getMessage();
        return message == null || message.isBlank() ? fallback : message;
    }
}
