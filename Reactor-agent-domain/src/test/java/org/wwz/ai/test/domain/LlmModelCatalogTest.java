package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.core.env.Environment;
import org.wwz.ai.domain.agent.adapter.repository.ILlmModelConfigRepository;
import org.wwz.ai.domain.agent.runtime.ReactorLlmDependencies;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.runtime.llm.LlmModelBinding;
import org.wwz.ai.domain.agent.runtime.llm.LlmModelCatalog;
import org.wwz.ai.domain.agent.runtime.llm.LLMSettings;

import java.util.List;
import java.util.Optional;

public class LlmModelCatalogTest {

    private static final LlmModelBinding GROK = LlmModelBinding.builder()
            .modelId("m-grok")
            .modelName("grok-4.5")
            .apiId("api-1")
            .baseUrl("https://example.com/v1")
            .apiKey("sk-test")
            .completionsPath("/chat/completions")
            .contextWindow(1_000_000)
            .build();

    private static final LlmModelBinding GPT = LlmModelBinding.builder()
            .modelId("m-gpt")
            .modelName("gpt-4o")
            .apiId("api-1")
            .modelUsage("fallback")
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
    public void defaultPickSkipsFallbackModel() {
        Assert.assertSame(GROK, LlmModelCatalog.pick(List.of(GPT, GROK), null));
    }

    @Test
    public void fallbackModelComesFromDatabaseUsage() {
        ILlmModelConfigRepository repository = Mockito.mock(ILlmModelConfigRepository.class);
        Mockito.when(repository.listUsable()).thenReturn(List.of(GROK, GPT));
        LlmModelCatalog catalog = new LlmModelCatalog(
                repository,
                null,
                0L);

        Assert.assertEquals("m-gpt", catalog.resolveFallbackModelName("grok-4.5").orElse(null));
    }

    @Test
    public void toSettingsMapsBinding() {
        LLMSettings settings = LlmModelCatalog.toSettings(GROK);
        Assert.assertEquals("grok-4.5", settings.getModel());
        Assert.assertEquals("https://example.com/v1", settings.getBaseUrl());
        Assert.assertEquals("sk-test", settings.getApiKey());
        Assert.assertEquals("/chat/completions", settings.getInterfaceUrl());
        Assert.assertEquals(1_000_000, settings.getMaxInputTokens());
    }

    @Test
    public void runtimeKeepsDatabaseContextWindowWhenMergingYmlDefaults() {
        LlmModelCatalog catalog = Mockito.mock(LlmModelCatalog.class);
        Mockito.when(catalog.resolve("m-grok")).thenReturn(Optional.of(
                LlmModelCatalog.toSettings(GROK)));

        Environment environment = Mockito.mock(Environment.class);
        ReactorRuntimeDependencies dependencies = ReactorRuntimeDependencies.builder()
                .environment(environment)
                .reactorConfig(null)
                .llmDependencies(ReactorLlmDependencies.builder()
                        .modelCatalog(catalog)
                        .build())
                .build();

        LLMSettings resolved = dependencies.resolveLlmSettings("m-grok");

        Assert.assertEquals(1_000_000, resolved.getMaxInputTokens());
    }
}
