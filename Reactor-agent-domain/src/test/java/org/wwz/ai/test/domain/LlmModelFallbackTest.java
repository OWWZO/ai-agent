package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.llm.LlmModelFallback;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.CancellationException;

public class LlmModelFallbackTest {

    @Test
    public void shouldFallbackOnTransientAndEmptyStream() {
        Assert.assertTrue(LlmModelFallback.isEligible(new SocketTimeoutException("read timed out")));
        Assert.assertTrue(LlmModelFallback.isEligible(new IOException("Upstream request failed")));
        Assert.assertTrue(LlmModelFallback.isEligible(
                new IllegalArgumentException("Empty response from streaming LLM (chunks=0)")));
        Assert.assertTrue(LlmModelFallback.isEligible(new RuntimeException("model overloaded")));
    }

    @Test
    public void shouldNotFallbackOnCancelAuthOrContextLimit() {
        Assert.assertFalse(LlmModelFallback.isEligible(new CancellationException("user_stop")));
        Assert.assertFalse(LlmModelFallback.isEligible(new RuntimeException("prompt_too_long")));
        Assert.assertFalse(LlmModelFallback.isEligible(new RuntimeException("context_length exceeded")));
        Assert.assertFalse(LlmModelFallback.isEligible(new RuntimeException("Unauthorized invalid api key")));
    }
}
