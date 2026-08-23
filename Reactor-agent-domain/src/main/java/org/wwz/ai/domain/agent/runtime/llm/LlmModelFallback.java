package org.wwz.ai.domain.agent.runtime.llm;

import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;

import java.util.Locale;
import java.util.concurrent.CancellationException;

/**
 * 主模型耗尽重试后的备援模型策略。
 * 仅对瞬态/容量类失败触发；取消、鉴权、证书、上下文超限等不切换。
 */
public final class LlmModelFallback {

    private static final String[] NON_FALLBACK_MARKERS = {
            "prompt is too long",
            "prompt_too_long",
            "context_length",
            "context length",
            "maximum context",
            "max context length",
            "certificate",
            "unauthorized",
            "invalid api key",
            "incorrect api key",
            "authentication",
            "permission denied",
            "forbidden",
            "user_stop",
            "aborted",
            "cancelled",
            "canceled"
    };

    private LlmModelFallback() {
    }

    public static String resolveFallbackModelName(ReactorRuntimeDependencies deps, String primaryModel) {
        if (deps == null) {
            return null;
        }
        try {
            if (deps.getLlmDependencies() == null || deps.getLlmDependencies().getModelCatalog() == null) {
                return null;
            }
            return deps.getLlmDependencies().getModelCatalog()
                    .resolveFallbackModelName(primaryModel)
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isEligible(Throwable throwable) {
        if (throwable == null) {
            return false;
        }
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof CancellationException
                    || current instanceof InterruptedException) {
                return false;
            }
            String message = current.getMessage();
            String lower = message == null ? "" : message.toLowerCase(Locale.ROOT);
            if (containsAny(lower, NON_FALLBACK_MARKERS)) {
                return false;
            }
            current = current.getCause();
        }
        // 与 LlmRequestRetry 一致：瞬态网络/5xx/空响应等可切备援
        return LlmRequestRetry.isTransient(throwable)
                || isEmptyResponse(throwable)
                || isModelUnavailable(throwable);
    }

    private static boolean isEmptyResponse(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase(Locale.ROOT);
                if (lower.contains("empty response")
                        || lower.contains("empty streaming")
                        || lower.contains("empty body")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean isModelUnavailable(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase(Locale.ROOT);
                if (lower.contains("model_not_found")
                        || lower.contains("model not found")
                        || lower.contains("does not exist")
                        || lower.contains("not available")
                        || lower.contains("high demand")
                        || lower.contains("capacity")
                        || lower.contains("overloaded")) {
                    return true;
                }
            }
            Integer status = extractStatusCode(current);
            if (status != null && (status == 404 || status == 529)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean containsAny(String text, String[] markers) {
        for (String marker : markers) {
            if (text.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static Integer extractStatusCode(Throwable throwable) {
        try {
            var method = throwable.getClass().getMethod("getStatusCode");
            Object value = method.invoke(throwable);
            if (value instanceof Integer integer) {
                return integer;
            }
            if (value != null) {
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
        return null;
    }
}
