package org.wwz.ai.domain.agent.runtime.llm;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.wwz.ai.domain.agent.adapter.repository.ILlmModelConfigRepository;

import java.util.List;

public class LlmModelCatalogDuplicateIdTest {

    @Test
    public void regularModelReferencePrefersPrimaryBinding() {
        LlmModelBinding primary = LlmModelBinding.builder()
                .id(1L)
                .modelId("gpt-5.6-luna")
                .modelName("gpt-5.6-luna")
                .modelUsage("default")
                .build();
        LlmModelBinding fallback = LlmModelBinding.builder()
                .id(2L)
                .modelId("gpt-5.6-luna")
                .modelName("gpt-5.6-luna")
                .modelUsage("fallback")
                .build();

        Assert.assertSame(primary, LlmModelCatalog.pick(List.of(primary, fallback), "gpt-5.6-luna"));
    }

    @Test
    public void duplicateModelIdFallbackUsesBindingRowReference() {
        LlmModelBinding primary = LlmModelBinding.builder()
                .id(1L)
                .modelId("gpt-5.6-luna")
                .modelName("gpt-5.6-luna")
                .modelUsage("default")
                .build();
        LlmModelBinding fallback = LlmModelBinding.builder()
                .id(2L)
                .modelId("gpt-5.6-luna")
                .modelName("gpt-5.6-luna")
                .modelUsage("fallback")
                .build();
        ILlmModelConfigRepository repository = Mockito.mock(ILlmModelConfigRepository.class);
        Mockito.when(repository.listUsable()).thenReturn(List.of(primary, fallback));
        LlmModelCatalog catalog = new LlmModelCatalog(repository, null, 0L);

        String rowReference = LlmModelCatalog.BINDING_REF_PREFIX + "2";
        Assert.assertEquals(List.of(rowReference), catalog.resolveFallbackModelNames("gpt-5.6-luna"));
        Assert.assertSame(fallback, LlmModelCatalog.pick(List.of(primary, fallback), rowReference));
    }
}
