package org.wwz.ai.domain.agent.runtime.llm;

import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;

import java.util.Locale;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

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
        return resolveFallbackModelNames(deps, primaryModel).stream().findFirst().orElse(null);
    }

    public static List<String> resolveFallbackModelNames(ReactorRuntimeDependencies deps, String primaryModel) {
        if (deps == null) {
            return List.of();
        }
        try {
            if (deps.getLlmDependencies() == null || deps.getLlmDependencies().getModelCatalog() == null) {
                return List.of();
            }
            return deps.getLlmDependencies().getModelCatalog().resolveFallbackModelNames(primaryModel);
        } catch (Exception e) {
            return List.of();
        }
    }

    @FunctionalInterface
    interface AttemptListener {
        void onAttempt(String fromModel, String toModel, Throwable cause);
    }

    /**
     * 顺序执行备用模型候选。候选模型失败后，仅瞬态/容量类错误继续切换，候选耗尽时返回最后一次错误。
     */
    static <T> CompletableFuture<T> executeFallbackChain(
            String failedModel,
            Throwable failure,
            List<String> fallbackModels,
            Function<String, CompletableFuture<T>> fallbackCall,
            AttemptListener listener) {
        if (fallbackModels == null || fallbackModels.isEmpty()) {
            return CompletableFuture.failedFuture(failure);
        }
        return executeFallbackChain(
                failedModel,
                failure,
                fallbackModels,
                0,
                fallbackCall,
                listener
        );
    }

    private static <T> CompletableFuture<T> executeFallbackChain(
            String failedModel,
            Throwable failure,
            List<String> fallbackModels,
            int index,
            Function<String, CompletableFuture<T>> fallbackCall,
            AttemptListener listener) {
        if (index >= fallbackModels.size()) {
            return CompletableFuture.failedFuture(failure);
        }

        String fallbackModel = fallbackModels.get(index);
        if (listener != null) {
            listener.onAttempt(failedModel, fallbackModel, failure);
        }

        try {
            CompletableFuture<T> result = fallbackCall.apply(fallbackModel);
            return result.handle((value, error) -> {
                if (error == null) {
                    return CompletableFuture.completedFuture(value);
                }
                Throwable root = unwrap(error);
                if (!isEligible(root)) {
                    return CompletableFuture.<T>failedFuture(root);
                }
                return executeFallbackChain(
                        fallbackModel,
                        root,
                        fallbackModels,
                        index + 1,
                        fallbackCall,
                        listener
                );
            }).thenCompose(next -> next);
        } catch (Exception error) {
            Throwable root = unwrap(error);
            if (!isEligible(root)) {
                return CompletableFuture.failedFuture(root);
            }
            return executeFallbackChain(
                    fallbackModel,
                    root,
                    fallbackModels,
                    index + 1,
                    fallbackCall,
                    listener
            );
        }
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
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
