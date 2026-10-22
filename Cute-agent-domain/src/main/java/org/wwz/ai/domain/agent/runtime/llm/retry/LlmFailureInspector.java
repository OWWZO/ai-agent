package org.wwz.ai.domain.agent.runtime.llm.retry;

import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.PortUnreachableException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.channels.ClosedChannelException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * 失败事实的底层抽取器。
 *
 * <p>移植自 ZCode 的 {@code failure-inspection.ts} + {@code failure-tls.ts}：从任意异常对象里
 * 抽取状态码、错误码、响应头、Retry-After、取消/超时/网络/TLS/超窗信号。
 *
 * <p>与旧实现的关键区别：优先按 <b>异常类型层次</b> 判定（Java 里 SSLException、SocketTimeoutException、
 * CertificateException 的类层次是稳定契约），只在类型信息确实缺失时才回退到文案匹配。
 * 上游改文案不会影响分类结果。
 */
public final class LlmFailureInspector {

    private LlmFailureInspector() {
    }

    private static final Set<String> TIMEOUT_CODES = Set.of(
            "ETIMEDOUT", "ETIMEOUT", "UND_ERR_CONNECT_TIMEOUT", "UND_ERR_HEADERS_TIMEOUT", "UND_ERR_BODY_TIMEOUT");

    private static final Set<String> NETWORK_CODES = Set.of(
            "ECONNRESET", "ECONNREFUSED", "EAI_AGAIN", "ENOTFOUND", "ENETUNREACH", "EHOSTUNREACH",
            "UND_ERR_SOCKET", "UND_ERR_CONNECT_TIMEOUT");

    /** 上下文超限的稳定 code 标记（provider 侧契约，非文案）。 */
    private static final Set<String> CONTEXT_EXCEEDED_CODES = Set.of(
            "context_length_exceeded", "context_window_exceeded",
            "model_context_exceeded", "model_context_window_exceeded");

    /** 证书类失败的兜底文案：只在异常类型丢失（被包装成普通 RuntimeException）时使用。 */
    private static final String[] CERT_FAILURE_MARKERS = {
            "certificate_verify_failed",
            "certificate verify failed",
            "hostname mismatch",
            "pkix path building failed",
            "unable to find valid certification path",
            "wrong version number",
            "unsupported protocol",
            "unknown ca",
            "self signed certificate"
    };

    /** 上下文超限的兜底文案：OpenAI 兼容 provider 只在 400 文本里报告超窗，没有稳定 code。 */
    private static final String[] CONTEXT_EXCEEDED_MARKERS = {
            "model_context_window_exceeded",
            "prompt is too long",
            "range of input length should be"
    };

    /**
     * 剥掉 CompletableFuture 的包装层（CompletionException / ExecutionException），
     * 让分类看到真实失败原因。
     */
    public static Throwable unwrap(Throwable error) {
        Throwable current = error;
        int guard = 0;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null
                && current.getCause() != current
                && guard++ < 32) {
            current = current.getCause();
        }
        return current;
    }

    /** 按引用去重地展开异常因果链，避免自引用导致死循环。 */
    public static List<Throwable> causeChain(Throwable error) {
        List<Throwable> chain = new ArrayList<>(4);
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = error;
        while (current != null && seen.add(current)) {
            chain.add(current);
            Throwable next = current.getCause();
            current = next == current ? null : next;
        }
        return chain;
    }

    /** HTTP 状态码：先读业务异常自带字段，再反射读 Spring/WebClient 异常，最后才是文案兜底。 */
    public static Integer statusCode(Throwable error) {
        for (Throwable current : causeChain(unwrap(error))) {
            if (current instanceof LlmProviderBusinessException business && business.getStatusCode() != null) {
                return normalizeHttpStatus(business.getStatusCode());
            }
            Integer reflected = reflectStatusCode(current);
            if (reflected != null) {
                return reflected;
            }
        }
        return null;
    }

    private static Integer reflectStatusCode(Throwable throwable) {
        Object direct = invokeNoArg(throwable, "getStatusCode");
        Integer normalized = coerceStatusCode(direct);
        if (normalized != null) {
            return normalized;
        }

        Object raw = invokeNoArg(throwable, "getRawStatusCode");
        normalized = coerceStatusCode(raw);
        if (normalized != null) {
            return normalized;
        }

        Object response = invokeNoArg(throwable, "getResponse");
        if (response != null) {
            normalized = coerceStatusCode(invokeNoArg(response, "getStatusCode"));
            if (normalized != null) {
                return normalized;
            }
        }

        // 兼容 UnknownHttpStatusCodeException 等仅在 message 中带 [524] 的场景。
        String message = throwable.getMessage();
        if (message != null && message.toLowerCase(Locale.ROOT).contains("status")) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\[(\\d{3})]").matcher(message);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
            matcher = java.util.regex.Pattern.compile("\\b(\\d{3})\\b").matcher(message);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        }
        return null;
    }

    private static Integer coerceStatusCode(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer integer) {
            return normalizeHttpStatus(integer);
        }
        if (value instanceof Number number) {
            return normalizeHttpStatus(number.intValue());
        }
        // Spring HttpStatusCode#value()
        Object inner = invokeNoArg(value, "value");
        if (inner instanceof Number number) {
            return normalizeHttpStatus(number.intValue());
        }
        String text = String.valueOf(value);
        if (text.matches("\\d{3}")) {
            return normalizeHttpStatus(Integer.parseInt(text));
        }
        return null;
    }

    private static Integer normalizeHttpStatus(Integer value) {
        return value != null && value >= 100 && value <= 599 ? value : null;
    }

    /**
     * 稳定错误码：只认本域显式定义的标记异常，以及异常自带的 code 属性。
     * 这是替代文案匹配的主通道。
     */
    public static String errorCode(Throwable error) {
        for (Throwable current : causeChain(unwrap(error))) {
            if (current instanceof LlmTlsValidationException) {
                return LlmTlsValidationException.CODE;
            }
            if (current instanceof LlmProviderBusinessException) {
                return LlmProviderBusinessException.CODE;
            }
            Object code = invokeNoArg(current, "code");
            if (code instanceof String text && !text.isBlank()) {
                return text;
            }
            Object getCode = invokeNoArg(current, "getCode");
            if (getCode instanceof String text && !text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    /** 响应头：业务异常自带的最优先，其次反射读 Spring HttpHeaders / WebClient 异常。 */
    public static Map<String, String> responseHeaders(Throwable error) {
        for (Throwable current : causeChain(unwrap(error))) {
            if (current instanceof LlmProviderBusinessException business && !business.getResponseHeaders().isEmpty()) {
                return business.getResponseHeaders();
            }
            Map<String, String> headers = coerceHeaders(invokeNoArg(current, "getResponseHeaders"));
            if (headers != null) {
                return headers;
            }
            headers = coerceHeaders(invokeNoArg(current, "getHeaders"));
            if (headers != null) {
                return headers;
            }
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> coerceHeaders(Object value) {
        if (!(value instanceof Map<?, ?> map) || map.isEmpty()) {
            return null;
        }
        java.util.LinkedHashMap<String, String> result = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                continue;
            }
            Object raw = entry.getValue();
            String headerValue = raw instanceof List<?> list
                    ? (list.isEmpty() ? null : String.valueOf(list.get(0)))
                    : (raw == null ? null : String.valueOf(raw));
            if (headerValue != null) {
                result.put(key, headerValue);
            }
        }
        return result.isEmpty() ? null : result;
    }

    /**
     * Retry-After 解析：provider 同时返回 {@code x-should-retry: false} 时，
     * Retry-After 只能当诊断信息，不能驱动等待（移植自 ZCode）。
     */
    public static Long parseRetryAfterMs(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        if (isShouldRetryHeaderFalse(headers)) {
            return null;
        }
        Long numeric = parseNumericHeaderMs(findHeader(headers, "retry-after-ms"));
        if (numeric != null) {
            return numeric;
        }
        String value = findHeader(headers, "retry-after");
        if (value == null || value.isBlank()) {
            return null;
        }
        value = value.trim();
        try {
            double seconds = Double.parseDouble(value);
            return Math.max(0L, Math.round(seconds * 1000d));
        } catch (NumberFormatException ignore) {
            // 不是秒数，继续按 HTTP-date 解析。
        }
        try {
            long epochMs = java.time.ZonedDateTime.parse(value, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant().toEpochMilli();
            return Math.max(0L, epochMs - System.currentTimeMillis());
        } catch (Exception ignore) {
            return null;
        }
    }

    private static boolean isShouldRetryHeaderFalse(Map<String, String> headers) {
        String value = findHeader(headers, "x-should-retry");
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return "false".equals(normalized) || "0".equals(normalized);
    }

    private static Long parseNumericHeaderMs(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Math.max(0L, Math.round(Double.parseDouble(value.trim())));
        } catch (NumberFormatException ignore) {
            return null;
        }
    }

    private static String findHeader(Map<String, String> headers, String name) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().toLowerCase(Locale.ROOT).equals(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /** 取消判定：信号本身 + 异常类型 + 少量跨语言标记。 */
    public static boolean isAbortFailure(Throwable error, boolean aborted) {
        if (aborted) {
            return true;
        }
        for (Throwable current : causeChain(unwrap(error))) {
            if (current instanceof CancellationException || current instanceof InterruptedException) {
                return true;
            }
            if ("AbortError".equals(current.getClass().getSimpleName())) {
                return true;
            }
            String code = errorCodeOf(current);
            if ("ABORT_ERR".equalsIgnoreCase(code)) {
                return true;
            }
        }
        return false;
    }

    private static String errorCodeOf(Throwable throwable) {
        Object code = invokeNoArg(throwable, "code");
        if (code instanceof String text) {
            return text;
        }
        Object getCode = invokeNoArg(throwable, "getCode");
        return getCode instanceof String text ? text : null;
    }

    /** 超时判定：状态码 408、稳定 code、异常类型层次。 */
    public static boolean isTimeoutFailure(Throwable error, String code, Integer statusCode) {
        if (statusCode != null && statusCode == 408) {
            return true;
        }
        if (code != null && TIMEOUT_CODES.contains(code.toUpperCase(Locale.ROOT))) {
            return true;
        }
        for (Throwable current : causeChain(unwrap(error))) {
            if (current instanceof SocketTimeoutException
                    || current instanceof TimeoutException) {
                return true;
            }
            // Netty 的 ReadTimeoutException 继承自 TimeoutException，已被上面覆盖；
            // JDK HttpClient 的 HttpTimeoutException 不在其列，单独按类型名识别。
            if (current.getClass().getName().equals("java.net.http.HttpTimeoutException")) {
                return true;
            }
        }
        return false;
    }

    /** 网络故障：跨语言 code 标记（provider 业务码场景）。 */
    public static boolean isNetworkFailure(String code) {
        return code != null && NETWORK_CODES.contains(code.toUpperCase(Locale.ROOT));
    }

    /** 网络故障：Java 异常类型层次。 */
    public static boolean isNetworkThrowable(Throwable error) {
        for (Throwable current : causeChain(unwrap(error))) {
            if (current instanceof ConnectException
                    || current instanceof UnknownHostException
                    || current instanceof NoRouteToHostException
                    || current instanceof PortUnreachableException
                    || current instanceof SocketException
                    || current instanceof EOFException
                    || current instanceof ClosedChannelException) {
                return true;
            }
            // SSLException 继承自 IOException 而非 SocketException，握手类错误由 TLS 分支处理。
            if (current instanceof IOException && !isTlsThrowable(current)) {
                return true;
            }
        }
        return false;
    }

    /** 代理故障：稳定 code 含 PROXY，或异常类型/文案明确指向代理隧道。 */
    public static boolean isProxyFailure(String code) {
        return code != null && code.toUpperCase(Locale.ROOT).contains("PROXY");
    }

    public static boolean isProxyThrowable(Throwable error) {
        for (Throwable current : causeChain(unwrap(error))) {
            String typeName = current.getClass().getSimpleName().toLowerCase(Locale.ROOT);
            if (typeName.contains("proxy")) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains("proxy")) {
                return true;
            }
        }
        return false;
    }

    /** TLS 故障的跨语言 code 标记（移植自 ZCode 的 isTlsFailure）。 */
    public static boolean isTlsFailure(String code) {
        if (code == null) {
            return false;
        }
        String normalized = code.toUpperCase(Locale.ROOT);
        return normalized.contains("CERT")
                || normalized.contains("TLS")
                || "UNABLE_TO_VERIFY_LEAF_SIGNATURE".equals(normalized);
    }

    /**
     * 证书校验失败：配置性问题，重试永远不会成功。
     * 优先按证书异常类型层次判定，类型被吞掉时才回退文案。
     */
    public static boolean isCertificateValidationFailure(Throwable error) {
        for (Throwable current : causeChain(unwrap(error))) {
            if (current instanceof LlmTlsValidationException
                    || current instanceof CertificateException
                    || current instanceof java.security.cert.CertPathValidatorException) {
                return true;
            }
            String typeName = current.getClass().getName();
            if (typeName.startsWith("sun.security.validator.")
                    || typeName.contains("CertPathValidatorException")) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && containsAny(message.toLowerCase(Locale.ROOT), CERT_FAILURE_MARKERS)) {
                return true;
            }
        }
        return false;
    }

    /**
     * TLS 握手被打断：服务端主动终止、连接被重置等，属可重试瞬态故障。
     * 证书校验失败不算（由 {@link #isCertificateValidationFailure} 先行拦截）。
     */
    public static boolean isTlsHandshakeInterrupt(Throwable error) {
        if (isCertificateValidationFailure(error)) {
            return false;
        }
        for (Throwable current : causeChain(unwrap(error))) {
            if (isTlsThrowable(current)) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains("handshake")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTlsThrowable(Throwable throwable) {
        Class<?> type = throwable.getClass();
        while (type != null) {
            String name = type.getName();
            if (name.startsWith("javax.net.ssl.")
                    || name.startsWith("javax.security.cert.")
                    || name.contains("SSLException")) {
                return true;
            }
            type = type.getSuperclass();
        }
        return false;
    }

    /** 上下文超限：稳定 code 优先，其次 provider 契约文案。 */
    public static boolean isContextExceededFailure(Throwable error) {
        for (Throwable current : causeChain(unwrap(error))) {
            if (isContextExceededCode(errorCodeOf(current))) {
                return true;
            }
            if (current instanceof LlmProviderBusinessException business
                    && isContextExceededCode(business.getProviderCode())) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && isContextExceededMessage(message.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isContextExceededCode(String value) {
        return value != null && CONTEXT_EXCEEDED_CODES.contains(value.toLowerCase(Locale.ROOT));
    }

    private static boolean isContextExceededMessage(String message) {
        if (message == null || message.isEmpty()) {
            return false;
        }
        return containsAny(message, CONTEXT_EXCEEDED_MARKERS)
                || (message.contains("context") && message.contains("exceed"))
                || (message.contains("maximum context length")
                        && message.contains("tokens")
                        && (message.contains("requested") || message.contains("resulted")))
                || (message.contains("input token count")
                        && message.contains("exceed")
                        && message.contains("maximum number of tokens allowed"))
                || (message.contains("total message token length")
                        && message.contains("exceed")
                        && message.contains("model limit"));
    }

    /** provider 是否显式标注了可重试。 */
    public static boolean isProviderMarkedRetryable(Throwable error) {
        for (Throwable current : causeChain(unwrap(error))) {
            if (current instanceof LlmProviderBusinessException business && business.getRetryable() != null) {
                return business.getRetryable();
            }
            Object value = invokeNoArg(current, "isRetryable");
            if (value instanceof Boolean bool) {
                return bool;
            }
        }
        return false;
    }

    static boolean containsAny(String text, String[] markers) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (String marker : markers) {
            if (text.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    /** 反射调用无参方法；失败（无此方法/不可访问）返回 null，只表示「读不到」而非错误。 */
    private static Object invokeNoArg(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            java.lang.reflect.Method method = target.getClass().getMethod(methodName);
            // 异常类可能是包私有实现（如 Spring 内部包装类），放开访问避免误判为「读不到」。
            method.trySetAccessible();
            return method.invoke(target);
        } catch (Exception ignore) {
            return null;
        }
    }
}
