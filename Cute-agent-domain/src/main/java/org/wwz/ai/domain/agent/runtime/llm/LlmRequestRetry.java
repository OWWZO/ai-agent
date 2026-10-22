package org.wwz.ai.domain.agent.runtime.llm;

import org.wwz.ai.domain.agent.runtime.llm.retry.LlmFailureClassification;
import org.wwz.ai.domain.agent.runtime.llm.retry.LlmFailureClassifier;
import org.wwz.ai.domain.agent.runtime.llm.retry.LlmRetryDelay;
import org.wwz.ai.domain.agent.runtime.llm.retry.LlmRetryPolicy;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Java 主链路 LLM 请求的统一瞬态错误重试。
 *
 * <p>失败判定与退避计算已迁移到 {@code llm.retry} 包的结构化实现
 * （{@link LlmFailureClassifier} / {@link LlmRetryPolicy} / {@link LlmRetryDelay}），
 * 本类只保留「怎么重试」的编排：同步 call、异步 callAsync、取消传播。
 * 对外 API 未变，调用点无需改动。
 *
 * <p>相比旧实现的三点行为变化：
 * <ol>
 *   <li>失败先做结构化分类（异常类型/状态码/provider 业务码），英文文案匹配降级为兜底</li>
 *   <li>退避尊重 provider 的 {@code Retry-After}（限流场景不再被 maxDelay 压成几秒）</li>
 *   <li>jitter 改为在封顶值上乘 0.5–1.0 的系数</li>
 * </ol>
 *
 * <p>{@link #stream(String, Supplier)} 语义：同一订阅者只在尚未产出 chunk 前重开，
 * 避免把半截流拼进同一累积器；{@link #callAsync(String, Supplier)} 在整次操作失败后
 * 丢弃半截结果并新开请求，覆盖首 chunk 前与中途失败。
 */
public final class LlmRequestRetry {

    private static final Logger LOG = Logger.getLogger(LlmRequestRetry.class.getName());

    /** 进程级重试策略，启动时从环境变量/系统属性解析一次。 */
    private static final LlmRetryPolicy POLICY = LlmRetryPolicy.fromEnvironment();

    private LlmRequestRetry() {
    }

    /** 当前生效的重试策略（含环境变量覆盖后的结果）。 */
    public static LlmRetryPolicy policy() {
        return POLICY;
    }

    public static int maxRetries() {
        return POLICY.maxRetries();
    }

    public static long baseDelayMs() {
        return POLICY.baseDelayMs();
    }

    public static long maxDelayMs() {
        return POLICY.maxDelayMs();
    }

    /**
     * 结构化失败分类：调用方可据此拿到 reason / statusCode / providerCode / retryAfterMs，
     * 用于日志归因、前端提示与 workflow 策略裁决。
     */
    public static LlmFailureClassification classify(Throwable throwable) {
        return LlmFailureClassifier.classify(throwable, false);
    }

    /**
     * 是否值得重试。等价于 {@code classify(throwable).isRetryable()}，
     * 保留旧方法名以避免调用点改动（{@code LlmModelFallback} 依赖它）。
     */
    public static boolean isTransient(Throwable throwable) {
        if (throwable == null) {
            return false;
        }
        return LlmFailureClassifier.classify(throwable, false).isRetryable();
    }

    /**
     * 重试回调：attempt 为即将进行的 1-based 尝试序号，maxAttempts 为总尝试上限。
     * 例如首次失败后即将第 2 次请求时，attempt=2, maxAttempts=6。
     */
    @FunctionalInterface
    public interface RetryListener {
        void onRetry(String label, int attempt, int maxAttempts, Throwable error, long delayMs);
    }

    public static <T> T call(String label, Supplier<T> supplier) {
        return call(POLICY, label, supplier, null);
    }

    public static <T> T call(String label, Supplier<T> supplier, RetryListener listener) {
        return call(POLICY, label, supplier, listener);
    }

    /**
     * 使用显式策略重试：便于测试注入快速策略，也便于调用点对个别场景（后台任务）放宽预算。
     */
    public static <T> T call(LlmRetryPolicy policy, String label, Supplier<T> supplier, RetryListener listener) {
        int maxAttempts = policy.maxAttempts();
        RuntimeException lastError = null;
        // 普通调用每次失败都可以从头执行；只有达到次数上限或判定为永久错误才把异常交给上层。
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                return supplier.get();
            } catch (Exception ex) {
                RuntimeException error = ex instanceof RuntimeException runtime ? runtime : new RuntimeException(ex);
                lastError = error;
                LlmFailureClassification failure = LlmFailureClassifier.classify(error, false);
                if (attempt >= maxAttempts - 1 || !failure.isRetryable()) {
                    throw error;
                }
                long sleepMs = LlmRetryDelay.calculate(policy, attempt + 1, failure.retryAfterMs());
                logTransient(label, attempt, maxAttempts, failure, sleepMs);
                notifyRetry(listener, label, attempt + 2, maxAttempts, error, sleepMs);
                sleepQuietly(sleepMs);
            }
        }
        throw lastError != null ? lastError : new IllegalStateException("LLM retry exhausted without error");
    }

    public static <T> CompletableFuture<T> callAsync(String label, Supplier<CompletableFuture<T>> supplier) {
        return callAsync(POLICY, label, supplier, null);
    }

    /**
     * 对整次异步 LLM 调用重试：每次失败都丢弃本次 Future/流累积，再开一次新请求。
     * 与流式首 chunk 语义不同，中途已产出 chunk 仍可重试。
     */
    public static <T> CompletableFuture<T> callAsync(String label,
                                                     Supplier<CompletableFuture<T>> supplier,
                                                     RetryListener listener) {
        return callAsync(POLICY, label, supplier, listener);
    }

    /** 使用显式策略的异步重试。 */
    public static <T> CompletableFuture<T> callAsync(LlmRetryPolicy policy,
                                                     String label,
                                                     Supplier<CompletableFuture<T>> supplier,
                                                     RetryListener listener) {
        CancellableFuture<T> result = new CancellableFuture<>();
        callAsyncAttempt(result, policy, label, supplier, listener, 0);
        return result;
    }

    private static <T> void callAsyncAttempt(CancellableFuture<T> result,
                                             LlmRetryPolicy policy,
                                             String label,
                                             Supplier<CompletableFuture<T>> supplier,
                                             RetryListener listener,
                                             int attempt) {
        if (result.isCancelled()) {
            return;
        }
        int maxAttempts = policy.maxAttempts();
        CompletableFuture<T> started;
        try {
            started = supplier.get();
        } catch (Exception ex) {
            RuntimeException error = ex instanceof RuntimeException runtime ? runtime : new RuntimeException(ex);
            retryAsyncOrFail(result, policy, label, supplier, listener, attempt, maxAttempts, error);
            return;
        }
        if (started == null) {
            result.completeExceptionally(new IllegalStateException("LLM async supplier returned null"));
            return;
        }
        result.bind(started);
        started.whenComplete((value, error) -> {
            if (result.isCancelled()) {
                return;
            }
            if (error == null) {
                result.complete(value);
                return;
            }
            Throwable root = unwrapAsyncError(error);
            RuntimeException asRuntime = root instanceof RuntimeException runtime
                    ? runtime
                    : new RuntimeException(root);
            retryAsyncOrFail(result, policy, label, supplier, listener, attempt, maxAttempts, asRuntime);
        });
    }

    private static <T> void retryAsyncOrFail(CancellableFuture<T> result,
                                             LlmRetryPolicy policy,
                                             String label,
                                             Supplier<CompletableFuture<T>> supplier,
                                             RetryListener listener,
                                             int attempt,
                                             int maxAttempts,
                                             RuntimeException error) {
        LlmFailureClassification failure = LlmFailureClassifier.classify(error, false);
        if (attempt >= maxAttempts - 1 || !failure.isRetryable()) {
            result.completeExceptionally(error);
            return;
        }
        long sleepMs = LlmRetryDelay.calculate(policy, attempt + 1, failure.retryAfterMs());
        logTransient(label, attempt, maxAttempts, failure, sleepMs);
        notifyRetry(listener, label, attempt + 2, maxAttempts, error, sleepMs);
        CompletableFuture<Void> delayed = CompletableFuture.runAsync(
                () -> {
                },
                CompletableFuture.delayedExecutor(sleepMs, TimeUnit.MILLISECONDS));
        result.bind(delayed);
        delayed.whenComplete((ignored, delayError) -> {
            if (delayError == null && !result.isCancelled()) {
                callAsyncAttempt(result, policy, label, supplier, listener, attempt + 1);
            }
        });
    }

    /** Maps a request result while retaining cancellation of its source future. */
    public static <T, R> CompletableFuture<R> mapCancellable(CompletableFuture<T> source,
                                                             Function<T, R> mapper) {
        CancellableFuture<R> result = new CancellableFuture<>();
        result.bind(source);
        source.whenComplete((value, error) -> {
            if (error != null) {
                result.completeExceptionally(error);
            } else {
                try {
                    result.complete(mapper.apply(value));
                } catch (Throwable mappingError) {
                    result.completeExceptionally(mappingError);
                }
            }
        });
        return result;
    }

    /** Keeps cancellation of a composed public future connected to its active request. */
    public static <T> CompletableFuture<T> propagateCancellation(CompletableFuture<T> result,
                                                                 CompletableFuture<?> activeRequest) {
        CancellableFuture<T> bridged = new CancellableFuture<>();
        bridged.bind(activeRequest);
        result.whenComplete((value, error) -> {
            if (error != null) {
                bridged.completeExceptionally(error);
            } else {
                bridged.complete(value);
            }
        });
        return bridged;
    }

    private static final class CancellableFuture<T> extends CompletableFuture<T> {
        private final AtomicReference<CompletableFuture<?>> delegate = new AtomicReference<>();

        private void bind(CompletableFuture<?> future) {
            delegate.set(future);
            if (isCancelled()) {
                future.cancel(true);
            }
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean cancelled = super.cancel(mayInterruptIfRunning);
            CompletableFuture<?> current = delegate.get();
            if (cancelled && current != null) {
                current.cancel(mayInterruptIfRunning);
            }
            return cancelled;
        }
    }

    private static Throwable unwrapAsyncError(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null
                && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static void logTransient(String label,
                                     int attempt,
                                     int maxAttempts,
                                     LlmFailureClassification failure,
                                     long sleepMs) {
        // 保留旧日志前缀，同时补上结构化归因字段，便于按 reason/code 聚合。
        LOG.log(Level.WARNING, String.format(
                "[%s] transient failure (attempt %d/%d): %s; reason=%s code=%s status=%s providerCode=%s; retry in %dms",
                label,
                attempt + 1,
                maxAttempts,
                failure.message(),
                failure.reason(),
                failure.code(),
                failure.statusCode(),
                failure.providerCode(),
                sleepMs));
    }

    private static void notifyRetry(RetryListener listener,
                                    String label,
                                    int attempt,
                                    int maxAttempts,
                                    Throwable error,
                                    long delayMs) {
        if (listener == null) {
            return;
        }
        try {
            listener.onRetry(label, attempt, maxAttempts, error, delayMs);
        } catch (Exception notifyError) {
            LOG.log(Level.FINE, "LLM retry listener failed: " + notifyError.getMessage(), notifyError);
        }
    }

    private static void sleepQuietly(long sleepMs) {
        if (sleepMs <= 0) {
            return;
        }
        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new CancellationException("LLM retry interrupted");
        }
    }
}
