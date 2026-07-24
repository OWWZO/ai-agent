package org.wwz.ai.test.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.ai.openai.api.OpenAiApi;
import org.wwz.ai.domain.agent.runtime.llm.LlmUsageSnapshot;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 优先解析接口 usage 明细。
 */
public class LlmUsageSnapshotTest {

    @Test
    public void test_fromObjectParsesResponseApiUsageShape() {
        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("prompt_tokens", 103);
        usage.put("completion_tokens", 48);
        usage.put("total_tokens", 151);

        Map<String, Object> promptDetails = new LinkedHashMap<>();
        promptDetails.put("cached_tokens", 12);
        promptDetails.put("text_tokens", 100);
        promptDetails.put("audio_tokens", 0);
        promptDetails.put("image_tokens", 3);
        usage.put("prompt_tokens_details", promptDetails);

        Map<String, Object> completionDetails = new LinkedHashMap<>();
        completionDetails.put("text_tokens", 40);
        completionDetails.put("audio_tokens", 0);
        completionDetails.put("reasoning_tokens", 8);
        usage.put("completion_tokens_details", completionDetails);

        LlmUsageSnapshot snapshot = LlmUsageSnapshot.fromObject(usage);
        Assert.assertEquals(Integer.valueOf(103), snapshot.getPromptTokens());
        Assert.assertEquals(Integer.valueOf(48), snapshot.getCompletionTokens());
        Assert.assertEquals(Integer.valueOf(151), snapshot.getTotalTokens());
        Assert.assertEquals(Integer.valueOf(12), snapshot.getCachedPromptTokens());
        Assert.assertEquals(Integer.valueOf(100), snapshot.getPromptTextTokens());
        Assert.assertEquals(Integer.valueOf(0), snapshot.getPromptAudioTokens());
        Assert.assertEquals(Integer.valueOf(3), snapshot.getPromptImageTokens());
        Assert.assertEquals(Integer.valueOf(40), snapshot.getCompletionTextTokens());
        Assert.assertEquals(Integer.valueOf(0), snapshot.getCompletionAudioTokens());
        Assert.assertEquals(Integer.valueOf(8), snapshot.getReasoningTokens());
    }

    @Test
    public void test_fromJsonNodeParsesSameShape() throws Exception {
        String json = """
                {
                  "prompt_tokens": 50,
                  "completion_tokens": 10,
                  "total_tokens": 60,
                  "prompt_tokens_details": {
                    "cached_tokens": 20,
                    "text_tokens": 50,
                    "audio_tokens": 0,
                    "image_tokens": 0
                  },
                  "completion_tokens_details": {
                    "text_tokens": 6,
                    "audio_tokens": 0,
                    "reasoning_tokens": 4
                  }
                }
                """;
        LlmUsageSnapshot snapshot = LlmUsageSnapshot.fromJsonNode(new ObjectMapper().readTree(json));
        Assert.assertEquals(Integer.valueOf(50), snapshot.getPromptTokens());
        Assert.assertEquals(Integer.valueOf(20), snapshot.getCachedPromptTokens());
        Assert.assertEquals(Integer.valueOf(4), snapshot.getReasoningTokens());
        Assert.assertEquals(Integer.valueOf(6), snapshot.getCompletionTextTokens());
    }

    @Test
    public void test_mergeLatestPrefersIncomingNonNull() {
        LlmUsageSnapshot base = LlmUsageSnapshot.builder()
                .promptTokens(1)
                .totalTokens(3)
                .build();
        LlmUsageSnapshot incoming = LlmUsageSnapshot.builder()
                .promptTokens(10)
                .completionTokens(20)
                .cachedPromptTokens(5)
                .build();
        LlmUsageSnapshot merged = base.mergeLatest(incoming);
        Assert.assertEquals(Integer.valueOf(10), merged.getPromptTokens());
        Assert.assertEquals(Integer.valueOf(20), merged.getCompletionTokens());
        Assert.assertEquals(Integer.valueOf(30), merged.getTotalTokens());
        Assert.assertEquals(Integer.valueOf(5), merged.getCachedPromptTokens());
    }

    @Test
    public void test_fromObjectParsesSpringAiOpenAiUsageRecord() {
        OpenAiApi.Usage usage = new OpenAiApi.Usage(
                22,
                80,
                102,
                new OpenAiApi.Usage.PromptTokensDetails(0, 18),
                new OpenAiApi.Usage.CompletionTokenDetails(17, null, 0, null)
        );

        LlmUsageSnapshot snapshot = LlmUsageSnapshot.fromObject(usage);
        Assert.assertEquals(Integer.valueOf(80), snapshot.getPromptTokens());
        Assert.assertEquals(Integer.valueOf(22), snapshot.getCompletionTokens());
        Assert.assertEquals(Integer.valueOf(102), snapshot.getTotalTokens());
        Assert.assertEquals(Integer.valueOf(18), snapshot.getCachedPromptTokens());
        Assert.assertEquals(Integer.valueOf(0), snapshot.getPromptAudioTokens());
        Assert.assertEquals(Integer.valueOf(17), snapshot.getReasoningTokens());
        Assert.assertEquals(Integer.valueOf(0), snapshot.getCompletionAudioTokens());
    }
}
