package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.llm.LlmUserFacingError;

public class LlmUserFacingErrorTest {

    @Test
    public void shouldMapCloudflare524ToTimeout() {
        Assert.assertEquals(
                "大模型请求超时，请稍后重试",
                LlmUserFacingError.toUserMessage(
                        new RuntimeException("Unknown status code [524]")));
    }

    @Test
    public void shouldMap503ToUnavailable() {
        Assert.assertEquals(
                "大模型服务暂时不可用，请稍后重试",
                LlmUserFacingError.toUserMessage(
                        new RuntimeException("503 Service Unavailable from POST https://example.com/v1/chat/completions")));
    }

    @Test
    public void shouldMapRateLimit() {
        Assert.assertEquals(
                "大模型请求过于频繁，请稍后重试",
                LlmUserFacingError.toUserMessage(new RuntimeException("429 Too Many Requests")));
    }

    @Test
    public void shouldFallbackForUnknown() {
        Assert.assertEquals(
                "大模型请求失败，请稍后重试",
                LlmUserFacingError.toUserMessage(new RuntimeException("something weird")));
    }
}
