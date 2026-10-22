package org.wwz.ai.domain.agent.runtime.llm;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将 LLM 调用异常映射为前端可展示的中文提示，避免把网关状态码/堆栈原文直接推给用户。
 */
public final class LlmUserFacingError {

    private static final Pattern STATUS_IN_MESSAGE = Pattern.compile("(?:status\\s*code|http\\s*status)[^0-9]{0,16}(\\d{3})",
            Pattern.CASE_INSENSITIVE);

    private static final Set<Integer> TIMEOUT_STATUS_CODES = Set.of(408, 504, 522, 524);
    private static final Set<Integer> UNAVAILABLE_STATUS_CODES = Set.of(500, 502, 503, 520, 521, 523, 525, 527);
    private static final Set<Integer> RATE_LIMIT_STATUS_CODES = Set.of(429);

    private LlmUserFacingError() {
    }

    public static String toUserMessage(Throwable throwable) {
        if (throwable == null) {
            return "大模型请求失败，请稍后重试";
        }

        Integer status = extractStatusCode(throwable);
        String lower = collectMessages(throwable).toLowerCase(Locale.ROOT);

        if (status != null && TIMEOUT_STATUS_CODES.contains(status)
                || containsAny(lower, "timeout", "timed out", "gateway timeout", "read timed out", "connect timed out")) {
            return "大模型请求超时，请稍后重试";
        }
        if (status != null && RATE_LIMIT_STATUS_CODES.contains(status)
                || containsAny(lower, "too many requests", "rate limit", "rate_limit")) {
            return "大模型请求过于频繁，请稍后重试";
        }
        if (status != null && UNAVAILABLE_STATUS_CODES.contains(status)
                || containsAny(lower, "service unavailable", "bad gateway", "temporarily unavailable", "overloaded")) {
            return "大模型服务暂时不可用，请稍后重试";
        }
        if (containsAny(lower, "connection reset", "connection aborted", "connection error",
                "connection closed", "broken pipe", "premature close", "unexpected end-of-input",
                "empty response", "empty body", "response body is empty")) {
            return "大模型连接中断，请稍后重试";
        }
        return "大模型请求失败，请稍后重试";
    }

    private static String collectMessages(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(current.getMessage());
            }
            current = current.getCause();
        }
        return sb.toString();
    }

    private static Integer extractStatusCode(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            Integer fromMethod = invokeStatusCode(current);
            if (fromMethod != null) {
                return fromMethod;
            }
            String message = current.getMessage();
            if (message != null) {
                Matcher matcher = STATUS_IN_MESSAGE.matcher(message);
                if (matcher.find()) {
                    return Integer.parseInt(matcher.group(1));
                }
                // Unknown status code [524]
                Matcher bracket = Pattern.compile("\\[(\\d{3})]").matcher(message);
                if (message.toLowerCase(Locale.ROOT).contains("status") && bracket.find()) {
                    return Integer.parseInt(bracket.group(1));
                }
            }
            current = current.getCause();
        }
        return null;
    }

    private static Integer invokeStatusCode(Throwable throwable) {
        try {
            var method = throwable.getClass().getMethod("getStatusCode");
            Object value = method.invoke(throwable);
            if (value instanceof Integer integer) {
                return integer;
            }
            if (value != null) {
                String text = String.valueOf(value);
                if (text.matches("\\d+")) {
                    return Integer.parseInt(text);
                }
                try {
                    var valueMethod = value.getClass().getMethod("value");
                    Object raw = valueMethod.invoke(value);
                    if (raw instanceof Integer integer) {
                        return integer;
                    }
                } catch (Exception ignore) {
                    // ignore
                }
            }
        } catch (Exception ignore) {
            // ignore
        }
        try {
            var method = throwable.getClass().getMethod("getRawStatusCode");
            Object value = method.invoke(throwable);
            if (value instanceof Integer integer) {
                return integer;
            }
        } catch (Exception ignore) {
            // ignore
        }
        return null;
    }

    private static boolean containsAny(String text, String... markers) {
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
}
