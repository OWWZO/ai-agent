package org.wwz.ai.domain.agent.runtime.llm;

import org.wwz.ai.domain.agent.ledger.model.AgentRunState;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;

import java.util.Locale;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

/**
 * 主模型耗尽重试后的备援模型策略。
 * 瞬态网络、鉴权/证书和容量类失败可切换；取消、上下文超限不切换。
 */
public final class LlmModelFallback {

    private static final String[] NON_FALLBACK_MARKERS = {
            "prompt is too long",
            "prompt_too_long",
            "context_length",
            "context length",
            "maximum context",
            "max context length",
            "user_stop",
            "cancelled",
            "canceled"
    };

    private static final String[] AUTH_OR_CERT_MARKERS = {
            "unauthorized",
            "invalid api key",
            "incorrect api key",
            "authentication",
            "permission denied",
            "forbidden",
            "certificate",
            "pkix path building failed",
            "unable to find valid certification path",
            "hostname mismatch",
            "unknown ca",
            "self signed certificate"
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
     * 主模型 Future 失败后按资格进入备用链；成功则原样返回。
     * 这是 LLM.withFallbackModel 的可测入口。
     */
    static <T> CompletableFuture<T> afterPrimary(
            LlmExecutionPosition position,
            AgentRunState state,
            String primaryModel,
            CompletableFuture<T> primary,
            List<String> fallbackModels,
            Function<String, CompletableFuture<T>> fallbackCall,
            AttemptListener listener) {
        if (primary == null) {
            return null;
        }
        LlmExecutionPosition captured = position == null ? LlmExecutionPosition.capture(state) : position;
        return primary.handle((result, error) -> {
            if (error == null) {
                return CompletableFuture.completedFuture(result);
            }
            Throwable root = unwrap(error);
            if (!isEligible(root) || fallbackModels == null || fallbackModels.isEmpty()) {
                return CompletableFuture.<T>failedFuture(root);
            }
            return recoverWithPosition(
                    captured,
                    state,
                    primaryModel,
                    root,
                    fallbackModels,
                    fallbackCall,
                    listener
            );
        }).thenCompose(next -> next);
    }

    /**
     * 在捕获的执行现场上跑备用链，避免 fallback 回调线程丢失 agentName/stepNo。
     */
    static <T> CompletableFuture<T> recoverWithPosition(
            LlmExecutionPosition position,
            AgentContext context,
            String failedModel,
            Throwable failure,
            List<String> fallbackModels,
            Function<String, CompletableFuture<T>> fallbackCall,
            AttemptListener listener) {
        return recoverWithPosition(
                position,
                context == null ? null : context.getAgentRunState(),
                failedModel,
                failure,
                fallbackModels,
                fallbackCall,
                listener
        );
    }

    static <T> CompletableFuture<T> recoverWithPosition(
            LlmExecutionPosition position,
            AgentRunState state,
            String failedModel,
            Throwable failure,
            List<String> fallbackModels,
            Function<String, CompletableFuture<T>> fallbackCall,
            AttemptListener listener) {
        LlmExecutionPosition captured = position == null ? LlmExecutionPosition.capture(state) : position;
        return executeFallbackChain(
                failedModel,
                failure,
                fallbackModels,
                fallbackName -> {
                    try (LlmExecutionPosition.Scope ignored = captured.restore(state)) {
                        return fallbackCall.apply(fallbackName);
                    }
                },
                listener
        );
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
        return LlmRequestRetry.isTransient(throwable)
                || isEmptyResponse(throwable)
                || isModelUnavailable(throwable)
                || isAuthOrCertificateFailure(throwable);
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

    private static boolean isAuthOrCertificateFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            String lower = message == null ? "" : message.toLowerCase(Locale.ROOT);
            if (containsAny(lower, AUTH_OR_CERT_MARKERS)) {
                return true;
            }
            Integer status = extractStatusCode(current);
            if (status != null && (status == 401 || status == 403)) {
                return true;
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
