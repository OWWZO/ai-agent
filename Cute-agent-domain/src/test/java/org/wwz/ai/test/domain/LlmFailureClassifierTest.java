package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.llm.LlmRequestRetry;
import org.wwz.ai.domain.agent.runtime.llm.retry.LlmEmptyCompletion;
import org.wwz.ai.domain.agent.runtime.llm.retry.LlmErrorCode;
import org.wwz.ai.domain.agent.runtime.llm.retry.LlmFailureClassification;
import org.wwz.ai.domain.agent.runtime.llm.retry.LlmFailureClassifier;
import org.wwz.ai.domain.agent.runtime.llm.retry.LlmFailureReason;
import org.wwz.ai.domain.agent.runtime.llm.retry.LlmProviderBusinessException;
import org.wwz.ai.domain.agent.runtime.llm.retry.LlmRetryBudget;
import org.wwz.ai.domain.agent.runtime.llm.retry.LlmRetryDelay;
import org.wwz.ai.domain.agent.runtime.llm.retry.LlmRetryPolicy;
import org.wwz.ai.domain.agent.runtime.llm.retry.LlmWorkflowFailurePolicy;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.security.cert.CertificateException;
import java.util.Map;
import java.util.concurrent.CancellationException;

/**
 * 结构化失败分类与重试策略的移植验证。
 */
public class LlmFailureClassifierTest {

    /** 与默认策略同形但退避为毫秒级：重试用例瞬间跑完，默认值单独断言。 */
    private static final LlmRetryPolicy FAST_POLICY = new LlmRetryPolicy(11, 1L, 2d, 4L, false);

    /** 测试用 HTTP 状态异常：验证反射抽取状态码的通道。 */
    public static final class HttpStatusException extends RuntimeException {
        private final int status;

        public HttpStatusException(int status, String message) {
            super(message);
            this.status = status;
        }

        public Integer getStatusCode() {
            return status;
        }
    }

    /** 测试用带头异常的响应头载体。 */
    public static final class HeaderedException extends RuntimeException {
        private final int status;
        private final Map<String, String> headers;

        public HeaderedException(int status, Map<String, String> headers) {
            super("headered failure");
            this.status = status;
            this.headers = headers;
        }

        public Integer getStatusCode() {
            return status;
        }

        public Map<String, String> getResponseHeaders() {
            return headers;
        }
    }

    @Test
    public void shouldClassifyRateLimitWithRetryAfterHeader() {
        LlmFailureClassification failure = LlmFailureClassifier.classify(
                new HeaderedException(429, Map.of("Retry-After", "30")));

        Assert.assertEquals(LlmErrorCode.MODEL_RATE_LIMITED, failure.code());
        Assert.assertEquals(LlmFailureReason.RATE_LIMITED, failure.reason());
        Assert.assertTrue(failure.isRetryable());
        Assert.assertEquals(Long.valueOf(30_000L), failure.retryAfterMs());
    }

    @Test
    public void shouldPreferRetryAfterMsHeaderAndHonourXShouldRetryFalse() {
        LlmFailureClassification millis = LlmFailureClassifier.classify(
                new HeaderedException(429, Map.of("retry-after-ms", "1500")));
        Assert.assertEquals(Long.valueOf(1500L), millis.retryAfterMs());

        LlmFailureClassification blocked = LlmFailureClassifier.classify(
                new HeaderedException(429, Map.of("Retry-After", "30", "x-should-retry", "false")));
        Assert.assertNull(blocked.retryAfterMs());
        Assert.assertTrue(blocked.isRetryable());
    }

    @Test
    public void shouldClassifyAuthAndServerErrors() {
        LlmFailureClassification unauthorized = LlmFailureClassifier.classify(
                new HttpStatusException(401, "Unauthorized"));
        Assert.assertEquals(LlmErrorCode.PROVIDER_NOT_CONFIGURED, unauthorized.code());
        Assert.assertEquals(LlmFailureReason.AUTH_FAILED, unauthorized.reason());
        Assert.assertFalse(unauthorized.isRetryable());

        LlmFailureClassification serverError = LlmFailureClassifier.classify(
                new HttpStatusException(503, "Service Unavailable"));
        Assert.assertEquals(LlmFailureReason.SERVER_ERROR, serverError.reason());
        Assert.assertTrue(serverError.isRetryable());

        LlmFailureClassification overloaded = LlmFailureClassifier.classify(
                new HttpStatusException(529, "Overloaded"));
        Assert.assertEquals(LlmFailureReason.PROVIDER_OVERLOADED, overloaded.reason());
        Assert.assertTrue(overloaded.isRetryable());
    }

    @Test
    public void shouldClassifyContextExceededFromStatusAndMessage() {
        LlmFailureClassification byStatus = LlmFailureClassifier.classify(
                new HttpStatusException(400, "context_length_exceeded"));
        Assert.assertEquals(LlmErrorCode.MODEL_CONTEXT_EXCEEDED, byStatus.code());
        Assert.assertEquals(LlmFailureReason.CONTEXT_EXCEEDED, byStatus.reason());
        Assert.assertFalse(byStatus.isRetryable());

        LlmFailureClassification byMessage = LlmFailureClassifier.classify(
                new HttpStatusException(400, "This model's maximum context length is 8192 tokens, however you requested 20000 tokens"));
        Assert.assertEquals(LlmErrorCode.MODEL_CONTEXT_EXCEEDED, byMessage.code());
        Assert.assertFalse(byMessage.isRetryable());

        LlmFailureClassification plainReject = LlmFailureClassifier.classify(
                new HttpStatusException(400, "unsupported parameter: seed"));
        Assert.assertEquals(LlmErrorCode.INVALID_MODEL_REQUEST, plainReject.code());
        Assert.assertFalse(plainReject.isRetryable());
    }

    @Test
    public void shouldClassifyNetworkAndTimeoutByExceptionType() {
        LlmFailureClassification network = LlmFailureClassifier.classify(new ConnectException("Connection refused"));
        Assert.assertEquals(LlmFailureReason.NETWORK_ERROR, network.reason());
        Assert.assertTrue(network.isRetryable());

        LlmFailureClassification timeout = LlmFailureClassifier.classify(new SocketTimeoutException("read timed out"));
        Assert.assertEquals(LlmFailureReason.TIMEOUT, timeout.reason());
        Assert.assertTrue(timeout.isRetryable());
    }

    @Test
    public void shouldSeparateTlsHandshakeInterruptFromCertificateFailure() {
        LlmFailureClassification handshake = LlmFailureClassifier.classify(
                new javax.net.ssl.SSLException("Remote host terminated the handshake"));
        Assert.assertEquals(LlmFailureReason.NETWORK_ERROR, handshake.reason());
        Assert.assertTrue(handshake.isRetryable());

        LlmFailureClassification certificate = LlmFailureClassifier.classify(
                new CertificateException("PKIX path building failed: unable to find valid certification path"));
        Assert.assertEquals(LlmFailureReason.TLS_ERROR, certificate.reason());
        Assert.assertFalse(certificate.isRetryable());

        LlmFailureClassification wrapped = LlmFailureClassifier.classify(
                new javax.net.ssl.SSLHandshakeException("PKIX path building failed"));
        Assert.assertEquals(LlmFailureReason.TLS_ERROR, wrapped.reason());
        Assert.assertFalse(wrapped.isRetryable());
    }

    @Test
    public void shouldClassifyCancellation() {
        LlmFailureClassification cancelled = LlmFailureClassifier.classify(new CancellationException("stopped"));
        Assert.assertEquals(LlmErrorCode.MODEL_REQUEST_CANCELLED, cancelled.code());
        Assert.assertTrue(cancelled.isCancelled());
        Assert.assertFalse(cancelled.isRetryable());

        // 取消优先于任何其他信号：即便带 429 状态码也不重试。
        LlmFailureClassification cancelledWithStatus = LlmFailureClassifier.classify(
                new CancellationException("stopped"), true);
        Assert.assertTrue(cancelledWithStatus.isCancelled());
    }

    @Test
    public void shouldMapProviderBusinessCodes() {
        LlmFailureClassification contextExceeded = LlmFailureClassifier.classify(business("1261", 200));
        Assert.assertEquals(LlmErrorCode.MODEL_CONTEXT_EXCEEDED, contextExceeded.code());
        Assert.assertFalse(contextExceeded.isRetryable());

        LlmFailureClassification retryableLimit = LlmFailureClassifier.classify(business("1302", 429));
        Assert.assertEquals(LlmFailureReason.RATE_LIMITED, retryableLimit.reason());
        Assert.assertTrue(retryableLimit.isRetryable());

        LlmFailureClassification concurrencyCap = LlmFailureClassifier.classify(business("3008", 429));
        Assert.assertEquals(LlmErrorCode.MODEL_RATE_LIMITED, concurrencyCap.code());
        Assert.assertFalse(concurrencyCap.isRetryable());

        LlmFailureClassification quota = LlmFailureClassifier.classify(business("insufficient_quota", 429));
        Assert.assertFalse(quota.isRetryable());

        LlmFailureClassification authCode = LlmFailureClassifier.classify(business("1006", 401));
        Assert.assertEquals(LlmFailureReason.AUTH_FAILED, authCode.reason());
        Assert.assertFalse(authCode.isRetryable());

        LlmFailureClassification networkCode = LlmFailureClassifier.classify(business("1234", 500));
        Assert.assertEquals(LlmFailureReason.NETWORK_ERROR, networkCode.reason());
        Assert.assertTrue(networkCode.isRetryable());

        LlmFailureClassification unknownCode = LlmFailureClassifier.classify(business("9999", 400));
        Assert.assertFalse(unknownCode.isRetryable());
        Assert.assertEquals("9999", unknownCode.providerCode());
    }

    @Test
    public void shouldKeepLegacyTransientBehaviourAsFallback() {
        // 旧实现依赖的英文文案匹配必须继续生效，否则线上重试行为会静默收紧。
        Assert.assertTrue(LlmRequestRetry.isTransient(new RuntimeException("Upstream request failed")));
        Assert.assertTrue(LlmRequestRetry.isTransient(new IOException("Connection reset")));
        Assert.assertTrue(LlmRequestRetry.isTransient(new RuntimeException("Unexpected end-of-input")));
        Assert.assertTrue(LlmRequestRetry.isTransient(
                new RuntimeException("JSON parse error: response body is empty")));
        Assert.assertTrue(LlmRequestRetry.isTransient(new RuntimeException("Unknown status code [524]")));
        Assert.assertFalse(LlmRequestRetry.isTransient(new IllegalArgumentException("invalid json payload")));
        Assert.assertFalse(LlmRequestRetry.isTransient(null));
    }

    @Test
    public void shouldComputeExponentialBackoffWithCap() {
        LlmRetryPolicy policy = new LlmRetryPolicy(11, 2_000L, 2d, 60_000L, false);
        Assert.assertEquals(2_000L, LlmRetryDelay.calculate(policy, 1));
        Assert.assertEquals(4_000L, LlmRetryDelay.calculate(policy, 2));
        Assert.assertEquals(8_000L, LlmRetryDelay.calculate(policy, 3));
        // 封顶：2s × 2^9 远超上限，被压到 60s
        Assert.assertEquals(60_000L, LlmRetryDelay.calculate(policy, 10));
    }

    @Test
    public void shouldPreferReasonableRetryAfterOverBackoff() {
        LlmRetryPolicy policy = new LlmRetryPolicy(11, 2_000L, 2d, 60_000L, false);
        // provider 要求等 90s：超过本地封顶，仍按 provider 给的值等待
        Assert.assertEquals(90_000L, LlmRetryDelay.calculate(policy, 1, 90_000L));
        // 超过 5 分钟且长于本地退避：视为不可信，退回本地退避
        Assert.assertEquals(2_000L, LlmRetryDelay.calculate(policy, 1, 600_000L));
        // 比本地退避短的小值：采信
        Assert.assertEquals(500L, LlmRetryDelay.calculate(policy, 1, 500L));
    }

    @Test
    public void shouldApplyJitterWithinHalfToFullRange() {
        LlmRetryPolicy policy = new LlmRetryPolicy(6, 1_000L, 2d, 4_000L, true);
        for (int i = 0; i < 50; i++) {
            long delay = LlmRetryDelay.calculate(policy, 1);
            Assert.assertTrue("jitter 应落在 [base/2, base] 区间，实际 " + delay,
                    delay >= 500L && delay <= 1_000L);
        }
    }

    @Test
    public void shouldGateRetriesByBudget() {
        // retryBudgetAttempt = 已消耗的尝试次数：maxAttempts=6 时，第 5 次失败后还能再试，第 6 次失败后不能。
        Assert.assertTrue(LlmRetryBudget.BOUNDED.allows(0, 6));
        Assert.assertTrue(LlmRetryBudget.BOUNDED.allows(5, 6));
        Assert.assertFalse(LlmRetryBudget.BOUNDED.allows(6, 6));
        Assert.assertTrue(LlmRetryBudget.UNBOUNDED.allows(999, 6));
        Assert.assertTrue(LlmRetryBudget.UNBOUNDED.loopContinues(999, 6));
        Assert.assertEquals(LlmRetryBudget.UNBOUNDED_MAX_ATTEMPTS_SENTINEL,
                LlmRetryBudget.UNBOUNDED.statusMaxAttempts(6));
        Assert.assertEquals(6, LlmRetryBudget.statusMaxAttempts(null, 6));
    }

    @Test
    public void shouldStopWorkflowOnlyOnDeterministicFailures() {
        // 配额类业务码：按码判，先于 reason
        LlmWorkflowFailurePolicy.Policy quota = LlmWorkflowFailurePolicy.resolve(
                LlmFailureClassifier.classify(business("1005", 429)), "1005");
        Assert.assertEquals(LlmWorkflowFailurePolicy.Decision.STOP, quota.decision());
        Assert.assertEquals(LlmWorkflowFailurePolicy.StopKind.QUOTA, quota.kind());

        // 鉴权失败：停下找人
        LlmWorkflowFailurePolicy.Policy auth = LlmWorkflowFailurePolicy.resolve(
                LlmFailureClassifier.classify(new HttpStatusException(401, "Unauthorized")), null);
        Assert.assertEquals(LlmWorkflowFailurePolicy.Decision.STOP, auth.decision());
        Assert.assertEquals(LlmWorkflowFailurePolicy.StopKind.AUTH, auth.kind());

        // 上下文超限：交给上层压缩
        LlmWorkflowFailurePolicy.Policy context = LlmWorkflowFailurePolicy.resolve(
                LlmFailureClassifier.classify(new HttpStatusException(400, "context_length_exceeded")), null);
        Assert.assertEquals(LlmWorkflowFailurePolicy.Decision.CONTEXT_EXCEEDED, context.decision());

        // 关键差异：TLS 在分类器里不可重试，但在无上限预算的后台任务里继续重试
        LlmFailureClassification tls = LlmFailureClassifier.classify(
                new CertificateException("PKIX path building failed"));
        Assert.assertFalse(tls.isRetryable());
        Assert.assertEquals(LlmWorkflowFailurePolicy.Decision.RETRY,
                LlmWorkflowFailurePolicy.resolve(tls, null).decision());
        Assert.assertFalse(LlmWorkflowFailurePolicy.retryAllowed(tls, LlmRetryBudget.BOUNDED, null));
        Assert.assertTrue(LlmWorkflowFailurePolicy.retryAllowed(tls, LlmRetryBudget.UNBOUNDED, null));

        // 响应解析失败不算「请求无效」：再问一次很可能就好
        LlmWorkflowFailurePolicy.Policy emptyResponse = LlmWorkflowFailurePolicy.resolve(
                LlmEmptyCompletion.failure(), null);
        Assert.assertEquals(LlmWorkflowFailurePolicy.Decision.RETRY, emptyResponse.decision());
    }

    @Test
    public void shouldGateEmptyCompletionRetry() {
        Assert.assertTrue(LlmEmptyCompletion.canRetry(false, 1, 6, 0));
        Assert.assertFalse(LlmEmptyCompletion.canRetry(true, 1, 6, 0));
        Assert.assertFalse(LlmEmptyCompletion.canRetry(false, 1, 6, 1));
        Assert.assertFalse(LlmEmptyCompletion.canRetry(false, 6, 6, 0));
        Assert.assertTrue(LlmEmptyCompletion.failure().isRetryable());
    }

    @Test
    public void shouldRetryThroughStructuredClassifier() {
        java.util.concurrent.atomic.AtomicInteger attempts = new java.util.concurrent.atomic.AtomicInteger();
        String result = LlmRequestRetry.call(FAST_POLICY, "test-structured", () -> {
            if (attempts.incrementAndGet() < 3) {
                throw new HttpStatusException(503, "Service Unavailable");
            }
            return "ok";
        }, null);
        Assert.assertEquals("ok", result);
        Assert.assertEquals(3, attempts.get());
        // 默认策略与 ZCode 对齐：10 次额外重试 / 2s 起步 / 60s 封顶
        Assert.assertEquals(10, LlmRequestRetry.maxRetries());
        Assert.assertEquals(2_000L, LlmRequestRetry.baseDelayMs());
        Assert.assertEquals(60_000L, LlmRequestRetry.maxDelayMs());
    }

    @Test
    public void shouldNotRetryPermanentFailure() {
        java.util.concurrent.atomic.AtomicInteger attempts = new java.util.concurrent.atomic.AtomicInteger();
        try {
            LlmRequestRetry.call(FAST_POLICY, "test-permanent", () -> {
                attempts.incrementAndGet();
                throw new HttpStatusException(400, "unsupported parameter: seed");
            }, null);
            Assert.fail("永久错误应当直接抛出");
        } catch (HttpStatusException expected) {
            Assert.assertEquals(1, attempts.get());
        }
    }

    private static LlmProviderBusinessException business(String providerCode, int statusCode) {
        return LlmProviderBusinessException.builder()
                .message("provider business error " + providerCode)
                .providerCode(providerCode)
                .statusCode(statusCode)
                .build();
    }
}
