package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.llm.LlmModelBinding;
import org.wwz.ai.domain.agent.runtime.llm.LlmModelCatalog;
import org.wwz.ai.domain.agent.runtime.llm.LLMSettings;

import java.util.List;

public class LlmModelCatalogTest {

    private static final LlmModelBinding GROK = LlmModelBinding.builder()
            .modelId("m-grok")
            .modelName("grok-4.5")
            .apiId("api-1")
            .baseUrl("https://example.com/v1")
            .apiKey("sk-test")
            .completionsPath("/chat/completions")
            .build();

    private static final LlmModelBinding GPT = LlmModelBinding.builder()
            .modelId("m-gpt")
            .modelName("gpt-4o")
            .apiId("api-1")
            .baseUrl("https://example.com/v1")
            .apiKey("sk-test")
            .build();

    @Test
    public void pickByModelId() {
        LlmModelBinding picked = LlmModelCatalog.pick(List.of(GROK, GPT), "m-gpt");
        Assert.assertSame(GPT, picked);
    }

    @Test
    public void pickByUpstreamModelName() {
        LlmModelBinding picked = LlmModelCatalog.pick(List.of(GROK, GPT), "grok-4.5");
        Assert.assertSame(GROK, picked);
    }

    @Test
    public void pickDefaultUsesFirst() {
        Assert.assertSame(GROK, LlmModelCatalog.pick(List.of(GROK, GPT), null));
        Assert.assertSame(GROK, LlmModelCatalog.pick(List.of(GROK, GPT), "default"));
        Assert.assertSame(GROK, LlmModelCatalog.pick(List.of(GROK, GPT), ""));
    }

    @Test
    public void pickUnknownDoesNotFallback() {
        Assert.assertNull(LlmModelCatalog.pick(List.of(GROK, GPT), "missing"));
    }

    @Test
    public void toSettingsMapsBinding() {
        LLMSettings settings = LlmModelCatalog.toSettings(GROK);
        Assert.assertEquals("grok-4.5", settings.getModel());
        Assert.assertEquals("https://example.com/v1", settings.getBaseUrl());
        Assert.assertEquals("sk-test", settings.getApiKey());
        Assert.assertEquals("/chat/completions", settings.getInterfaceUrl());
    }
}
