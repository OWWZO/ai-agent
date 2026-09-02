package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.llm.LlmModelFallback;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;

public class LlmModelFallbackTest {

    @Test
    public void shouldFallbackOnTransientAndEmptyStream() {
        Assert.assertTrue(LlmModelFallback.isEligible(new SocketTimeoutException("read timed out")));
        Assert.assertTrue(LlmModelFallback.isEligible(new IOException("Upstream request failed")));
        Assert.assertTrue(LlmModelFallback.isEligible(
                new IllegalArgumentException("Empty response from streaming LLM (chunks=0)")));
        Assert.assertTrue(LlmModelFallback.isEligible(new RuntimeException("model overloaded")));
        Assert.assertTrue(LlmModelFallback.isEligible(new TimeoutException("LLM stream timeout after 300s")));
        Assert.assertTrue(LlmModelFallback.isEligible(new RuntimeException("Unknown status code [524]")));
        Assert.assertTrue(LlmModelFallback.isEligible(new IOException("Connection reset")));
        Assert.assertTrue(LlmModelFallback.isEligible(new IOException("connection aborted")));
    }

    @Test
    public void shouldFallbackOnAuthAndCertificateErrors() {
        Assert.assertTrue(LlmModelFallback.isEligible(new RuntimeException("Unauthorized invalid api key")));
        Assert.assertTrue(LlmModelFallback.isEligible(new RuntimeException("permission denied")));
        Assert.assertTrue(LlmModelFallback.isEligible(new RuntimeException("Forbidden")));
        Assert.assertTrue(LlmModelFallback.isEligible(
                new javax.net.ssl.SSLHandshakeException(
                        "PKIX path building failed: unable to find valid certification path")));
        Assert.assertTrue(LlmModelFallback.isEligible(new RuntimeException("certificate verify failed")));
    }

    @Test
    public void shouldNotFallbackOnCancelOrContextLimit() {
        Assert.assertFalse(LlmModelFallback.isEligible(new CancellationException("user_stop")));
        Assert.assertFalse(LlmModelFallback.isEligible(new RuntimeException("LLM stream aborted: user_stop")));
        Assert.assertFalse(LlmModelFallback.isEligible(new RuntimeException("prompt_too_long")));
        Assert.assertFalse(LlmModelFallback.isEligible(new RuntimeException("context_length exceeded")));
    }
}
