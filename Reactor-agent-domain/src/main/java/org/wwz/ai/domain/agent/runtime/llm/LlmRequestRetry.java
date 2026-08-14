package org.wwz.ai.domain.agent.runtime.llm;

import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Java 主链路 LLM 请求的统一瞬态错误重试。
 * 默认最多额外重试 5 次；流式仅在尚未产出任何 chunk 前允许重开。
 */
public final class LlmRequestRetry {

    private static final Logger LOG = Logger.getLogger(LlmRequestRetry.class.getName());

    private static final int DEFAULT_MAX_RETRIES = 5;
    private static final long DEFAULT_BASE_DELAY_MS = 500L;
    private static final long DEFAULT_MAX_DELAY_MS = 4000L;

    private static final Set<Integer> TRANSIENT_STATUS_CODES = Set.of(408, 409, 425, 429, 500, 502, 503, 504);
    private static final String[] TRANSIENT_MARKERS = {
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
            "i/o error",
            "read timed out",
            "connect timed out",
            "ssl",
            "tls",
            "handshake",
            "unexpected_eof",
            "unexpected end-of-input",
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

    private static final String[] NON_TRANSIENT_MARKERS = {
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

    private LlmRequestRetry() {
    }

    public static int maxRetries() {
        return DEFAULT_MAX_RETRIES;
    }

    public static long baseDelayMs() {
        return DEFAULT_BASE_DELAY_MS;
    }

    public static long maxDelayMs() {
        return DEFAULT_MAX_DELAY_MS;
    }

    public static boolean isTransient(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            String lower = message == null ? "" : message.toLowerCase(Locale.ROOT);
            if (containsAny(lower, NON_TRANSIENT_MARKERS)) {
                // 证书、协议版本等配置性错误不会因重试改变，必须优先于通用 IOException 判断。
                return false;
            }

            // SSL/TLS 握手中断通常属于可重试瞬态故障。
            if (isSslException(current)) {
                return true;
            }

            String typeName = current.getClass().getSimpleName().toLowerCase(Locale.ROOT);
            if (typeName.contains("jsoneofexception")
                    || typeName.contains("eofexception")
                    || typeName.contains("prematureclose")
                    || typeName.contains("responseended")) {
                return true;
            }

            if (current instanceof SocketTimeoutException
                    || current instanceof SocketException
                    || current instanceof TimeoutException) {
                return true;
            }

            // 普通 IOException 仍可重试；证书类错误已在上面排除。
            if (current instanceof IOException) {
                // 网络 I/O 的异常类型层次不稳定，无法识别为永久错误时按瞬态处理。
                return true;
            }

            Integer statusCode = extractStatusCode(current);
            if (statusCode != null && TRANSIENT_STATUS_CODES.contains(statusCode)) {
                return true;
            }

            if (containsAny(lower, TRANSIENT_MARKERS)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean isSslException(Throwable throwable) {
        Class<?> type = throwable.getClass();
        while (type != null) {
            String name = type.getName();
            if (name.startsWith("javax.net.ssl.")
                    || name.startsWith("javax.security.cert.")
                    || name.contains("SSLException")
                    || name.contains("SSLHandshakeException")) {
                return true;
            }
            type = type.getSuperclass();
        }
        return false;
    }

    private static boolean containsAny(String text, String[] markers) {
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

    /**
     * 重试回调：attempt 为即将进行的 1-based 尝试序号，maxAttempts 为总尝试上限。
     * 例如首次失败后即将第 2 次请求时，attempt=2, maxAttempts=6。
     */
    @FunctionalInterface
    public interface RetryListener {
        void onRetry(String label, int attempt, int maxAttempts, Throwable error, long delayMs);
    }

    public static <T> T call(String label, Supplier<T> supplier) {
        return call(label, supplier, null);
    }

    public static <T> T call(String label, Supplier<T> supplier, RetryListener listener) {
        int retries = maxRetries();
        int maxAttempts = retries + 1;
        RuntimeException lastError = null;
        // 普通调用每次失败都可以从头执行；只有达到次数上限或判定为永久错误才把异常交给上层。
        for (int attempt = 0; attempt <= retries; attempt++) {
            try {
                return supplier.get();
            } catch (RuntimeException ex) {
                lastError = ex;
                if (attempt >= retries || !isTransient(ex)) {
                    throw ex;
                }
                long sleepMs = computeDelayMs(attempt);
                int nextAttempt = attempt + 2;
                LOG.log(Level.WARNING, String.format(
                        "[%s] transient failure (attempt %d/%d): %s; retry in %dms",
                        label, attempt + 1, maxAttempts, ex.getMessage(), sleepMs));
                notifyRetry(listener, label, nextAttempt, maxAttempts, ex, sleepMs);
                sleepQuietly(sleepMs);
            } catch (Exception ex) {
                RuntimeException wrapped = new RuntimeException(ex);
                lastError = wrapped;
                if (attempt >= retries || !isTransient(ex)) {
                    throw wrapped;
                }
                long sleepMs = computeDelayMs(attempt);
                int nextAttempt = attempt + 2;
                LOG.log(Level.WARNING, String.format(
                        "[%s] transient failure (attempt %d/%d): %s; retry in %dms",
                        label, attempt + 1, maxAttempts, ex.getMessage(), sleepMs));
                notifyRetry(listener, label, nextAttempt, maxAttempts, ex, sleepMs);
                sleepQuietly(sleepMs);
            }
        }
        throw lastError != null ? lastError : new IllegalStateException("LLM retry exhausted without error");
    }

    public static Flux<ChatResponse> stream(String label, Supplier<Flux<ChatResponse>> openStream) {
        return stream(label, openStream, null);
    }

    public static Flux<ChatResponse> stream(String label,
                                            Supplier<Flux<ChatResponse>> openStream,
                                            RetryListener listener) {
        AtomicBoolean emitted = new AtomicBoolean(false);
        int retries = maxRetries();
        int maxAttempts = retries + 1;
        // 流式一旦向客户端发出 chunk 就不能透明重开，否则会重复内容；因此 retry 条件绑定 emitted 状态。
        return Flux.defer(() -> {
                    emitted.set(false);
                    return openStream.get().doOnNext(ignored -> emitted.set(true));
                })
                .retryWhen(Retry.backoff(retries, Duration.ofMillis(baseDelayMs()))
                        .maxBackoff(Duration.ofMillis(maxDelayMs()))
                        .filter(error -> !emitted.get() && isTransient(error))
                        .doBeforeRetry(signal -> {
                            int failedAttempt = (int) signal.totalRetries() + 1;
                            int nextAttempt = failedAttempt + 1;
                            Throwable failure = signal.failure();
                            LOG.log(Level.WARNING, String.format(
                                    "[%s] stream transient failure before first chunk (attempt %d/%d): %s",
                                    label,
                                    failedAttempt,
                                    maxAttempts,
                                    failure == null ? "unknown" : failure.getMessage()
                            ));
                            notifyRetry(listener, label, nextAttempt, maxAttempts, failure, 0L);
                        }));
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

    private static long computeDelayMs(int attempt) {
        // 指数退避限制在 maxDelay 内，再加入小幅 jitter，减少多个请求同时重试造成的尖峰。
        long delay = Math.min(maxDelayMs(), baseDelayMs() * (1L << attempt));
        long jitter = (long) (Math.random() * Math.min(250L, Math.max(1L, delay / 5L)));
        return delay + jitter;
    }

    private static void sleepQuietly(long sleepMs) {
        if (sleepMs <= 0) {
            return;
        }
        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("LLM retry interrupted", interrupted);
        }
    }

    private static Integer extractStatusCode(Throwable throwable) {
        // 兼容 Spring HttpStatusCode、远端 SDK response 等不同异常 API，反射失败只表示无法取状态码。
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
                // Spring HttpStatusCode#value()
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
            var responseMethod = throwable.getClass().getMethod("getResponse");
            Object response = responseMethod.invoke(throwable);
            if (response != null) {
                try {
                    var statusMethod = response.getClass().getMethod("getStatusCode");
                    Object status = statusMethod.invoke(response);
                    if (status instanceof Integer integer) {
                        return integer;
                    }
                    if (status != null) {
                        var valueMethod = status.getClass().getMethod("value");
                        Object raw = valueMethod.invoke(status);
                        if (raw instanceof Integer integer) {
                            return integer;
                        }
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
