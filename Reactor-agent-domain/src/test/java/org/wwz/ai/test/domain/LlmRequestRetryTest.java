package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.ai.chat.model.ChatResponse;
import org.wwz.ai.domain.agent.runtime.llm.LlmRequestRetry;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class LlmRequestRetryTest {

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
        String result = LlmRequestRetry.call("test-call", () -> {
            if (attempts.incrementAndGet() <= 5) {
                throw new RuntimeException("Unexpected end-of-input");
            }
            return "ok";
        });
        Assert.assertEquals("ok", result);
        Assert.assertEquals(6, attempts.get());
        Assert.assertEquals(5, LlmRequestRetry.maxRetries());
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
        String result = LlmRequestRetry.call("test-call", () -> {
            if (attempts.incrementAndGet() < 3) {
                throw new RuntimeException("Upstream request failed");
            }
            return "ok";
        });
        Assert.assertEquals("ok", result);
        Assert.assertEquals(3, attempts.get());
    }

    @Test
    public void shouldNotifyListenerOnTransientRetry() {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger notifications = new AtomicInteger();
        java.util.List<Integer> notifiedAttempts = new java.util.ArrayList<>();
        String result = LlmRequestRetry.call("test-call", () -> {
            if (attempts.incrementAndGet() < 3) {
                throw new RuntimeException("Upstream request failed");
            }
            return "ok";
        }, (label, attempt, maxAttempts, error, delayMs) -> {
            notifications.incrementAndGet();
            notifiedAttempts.add(attempt);
            Assert.assertEquals("test-call", label);
            Assert.assertEquals(LlmRequestRetry.maxRetries() + 1, maxAttempts);
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
    public void shouldRetryStreamBeforeFirstChunk() {
        AtomicInteger attempts = new AtomicInteger();
        List<ChatResponse> responses = LlmRequestRetry.stream("test-stream", () -> {
            if (attempts.incrementAndGet() < 2) {
                return Flux.error(new RuntimeException("Upstream request failed"));
            }
            return Flux.just(new ChatResponse(List.of()));
        }).collectList().block();

        Assert.assertNotNull(responses);
        Assert.assertEquals(1, responses.size());
        Assert.assertEquals(2, attempts.get());
    }

    @Test
    public void shouldRetryStreamOn524BeforeFirstChunk() {
        AtomicInteger attempts = new AtomicInteger();
        List<ChatResponse> responses = LlmRequestRetry.stream("test-stream-524", () -> {
            if (attempts.incrementAndGet() < 2) {
                return Flux.error(new RuntimeException("Unknown status code [524]"));
            }
            return Flux.just(new ChatResponse(List.of()));
        }).collectList().block();

        Assert.assertNotNull(responses);
        Assert.assertEquals(1, responses.size());
        Assert.assertEquals(2, attempts.get());
    }

    @Test
    public void shouldRetryStreamOnConnectionResetBeforeFirstChunk() {
        AtomicInteger attempts = new AtomicInteger();
        List<ChatResponse> responses = LlmRequestRetry.stream("test-stream-reset", () -> {
            if (attempts.incrementAndGet() < 2) {
                return Flux.error(new IOException("Connection reset"));
            }
            return Flux.just(new ChatResponse(List.of()));
        }).collectList().block();

        Assert.assertNotNull(responses);
        Assert.assertEquals(1, responses.size());
        Assert.assertEquals(2, attempts.get());
    }

    @Test
    public void shouldNotRetryStreamAfterFirstChunk() {
        AtomicInteger attempts = new AtomicInteger();
        try {
            LlmRequestRetry.stream("test-stream", () -> {
                attempts.incrementAndGet();
                return Flux.concat(
                        Flux.just(new ChatResponse(List.of())),
                        Flux.error(new RuntimeException("Upstream request failed"))
                );
            }).collectList().block();
            Assert.fail("expected RuntimeException");
        } catch (RuntimeException expected) {
            Assert.assertEquals(1, attempts.get());
        }
    }
}
