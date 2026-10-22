package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.llm.LlmRequestRetry;
import org.wwz.ai.domain.agent.runtime.llm.retry.LlmRetryPolicy;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

public class LlmRequestRetryTest {

    /**
     * 与默认策略同形（11 次尝试）但退避为毫秒级：让重试相关用例瞬间跑完，
     * 生产默认值（2s 起步 / 60s 封顶）由 {@link #shouldAllowFiveAdditionalRetriesForTransientCall} 断言。
     */
    private static final LlmRetryPolicy FAST_POLICY = new LlmRetryPolicy(11, 1L, 2d, 4L, false);

    @Test
    public void shouldDetectUpstreamRequestFailedAsTransient() {
        Assert.assertTrue(LlmRequestRetry.isTransient(new RuntimeException("Upstream request failed")));
        Assert.assertTrue(LlmRequestRetry.isTransient(new IOException("Connection reset")));
        Assert.assertTrue(LlmRequestRetry.isTransient(new RuntimeException("Unexpected end-of-input")));
        Assert.assertTrue(LlmRequestRetry.isTransient(new RuntimeException("JSON parse error: response body is empty")));
        Assert.assertTrue(LlmRequestRetry.isTransient(new RuntimeException("Unknown status code [524]")));
        Assert.assertFalse(LlmRequestRetry.isTransient(new IllegalArgumentException("invalid json payload")));
    }

    @Test
    public void shouldAllowFiveAdditionalRetriesForTransientCall() {
        AtomicInteger attempts = new AtomicInteger();
        String result = LlmRequestRetry.call(FAST_POLICY, "test-call", () -> {
            if (attempts.incrementAndGet() <= 5) {
                throw new RuntimeException("Unexpected end-of-input");
            }
            return "ok";
        }, null);
        Assert.assertEquals("ok", result);
        Assert.assertEquals(6, attempts.get());
        // 默认策略与 ZCode 对齐：10 次额外重试（共 11 次尝试）
        Assert.assertEquals(10, LlmRequestRetry.maxRetries());
        Assert.assertEquals(2_000L, LlmRequestRetry.baseDelayMs());
        Assert.assertEquals(60_000L, LlmRequestRetry.maxDelayMs());
    }

    @Test
    public void shouldRetryTlsHandshakeInterruptButNotCertErrors() {
        Assert.assertTrue(LlmRequestRetry.isTransient(
                new javax.net.ssl.SSLException("Remote host terminated the handshake")));
        Assert.assertTrue(LlmRequestRetry.isTransient(
                new RuntimeException("TLS/SSL handshake interrupted")));
        Assert.assertFalse(LlmRequestRetry.isTransient(
                new javax.net.ssl.SSLHandshakeException(
                        "PKIX path building failed: unable to find valid certification path")));
    }

    @Test
    public void shouldRetryTransientErrorsForCall() {
        AtomicInteger attempts = new AtomicInteger();
        String result = LlmRequestRetry.call(FAST_POLICY, "test-call", () -> {
            if (attempts.incrementAndGet() < 3) {
                throw new RuntimeException("Upstream request failed");
            }
            return "ok";
        }, null);
        Assert.assertEquals("ok", result);
        Assert.assertEquals(3, attempts.get());
    }

    @Test
    public void shouldNotifyListenerOnTransientRetry() {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger notifications = new AtomicInteger();
        java.util.List<Integer> notifiedAttempts = new java.util.ArrayList<>();
        String result = LlmRequestRetry.call(FAST_POLICY, "test-call", () -> {
            if (attempts.incrementAndGet() < 3) {
                throw new RuntimeException("Upstream request failed");
            }
            return "ok";
        }, (label, attempt, maxAttempts, error, delayMs) -> {
            notifications.incrementAndGet();
            notifiedAttempts.add(attempt);
            Assert.assertEquals("test-call", label);
            Assert.assertEquals(FAST_POLICY.maxAttempts(), maxAttempts);
            Assert.assertNotNull(error);
            Assert.assertTrue(delayMs >= 0);
        });
        Assert.assertEquals("ok", result);
        Assert.assertEquals(2, notifications.get());
        Assert.assertEquals(java.util.List.of(2, 3), notifiedAttempts);
    }

    @Test
    public void shouldNotRetryNonTransientErrorsForCall() {
        AtomicInteger attempts = new AtomicInteger();
        try {
            LlmRequestRetry.call("test-call", () -> {
                attempts.incrementAndGet();
                throw new IllegalArgumentException("bad request");
            });
            Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            Assert.assertEquals(1, attempts.get());
        }
    }

    @Test
    public void shouldRetryCallAsyncAfterMidStreamTransientFailure() {
        AtomicInteger attempts = new AtomicInteger();
        String result = LlmRequestRetry.callAsync(FAST_POLICY, "test-call-async", () -> {
            int n = attempts.incrementAndGet();
            if (n < 3) {
                return CompletableFuture.failedFuture(new IOException("Connection reset after chunks"));
            }
            return CompletableFuture.completedFuture("ok");
        }, null).join();
        Assert.assertEquals("ok", result);
        Assert.assertEquals(3, attempts.get());
    }

    @Test
    public void shouldNotRetryCallAsyncForNonTransientErrors() {
        AtomicInteger attempts = new AtomicInteger();
        try {
            LlmRequestRetry.callAsync("test-call-async", () -> {
                attempts.incrementAndGet();
                return CompletableFuture.failedFuture(new IllegalArgumentException("bad request"));
            }).join();
            Assert.fail("expected CompletionException");
        } catch (CompletionException expected) {
            Assert.assertTrue(expected.getCause() instanceof IllegalArgumentException);
            Assert.assertEquals(1, attempts.get());
        }
    }

    @Test
    public void shouldCancelActiveAsyncAttemptWhenOuterFutureIsCancelled() throws Exception {
        AtomicBoolean cancelled = new AtomicBoolean();
        CompletableFuture<String> inner = new CompletableFuture<>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                cancelled.set(true);
                return super.cancel(mayInterruptIfRunning);
            }
        };

        CompletableFuture<String> outer = LlmRequestRetry.callAsync("cancel-test", () -> inner);
        Assert.assertTrue(outer.cancel(true));
        Assert.assertTrue(cancelled.get());
        Assert.assertTrue(inner.isCancelled());
    }

    @Test
    public void shouldCancelSourceWhenMappedFutureIsCancelled() {
        AtomicBoolean cancelled = new AtomicBoolean();
        CompletableFuture<String> source = new CompletableFuture<>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                cancelled.set(true);
                return super.cancel(mayInterruptIfRunning);
            }
        };

        CompletableFuture<Integer> mapped = LlmRequestRetry.mapCancellable(source, String::length);
        Assert.assertTrue(mapped.cancel(true));
        Assert.assertTrue(cancelled.get());
        Assert.assertTrue(source.isCancelled());
    }

}
