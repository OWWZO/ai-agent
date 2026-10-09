package org.wwz.ai.infrastructure.adapter.port.catalog;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.catalog.model.CatalogModel;
import org.wwz.ai.domain.agent.catalog.port.ICatalogModelRuntimePort;
import org.wwz.ai.domain.agent.runtime.llm.LlmModelCatalog;
import org.wwz.ai.domain.agent.runtime.llm.LlmModelCatalogEntry;

import java.util.List;

/** 模型目录运行时适配器。 */
@Component
@RequiredArgsConstructor
public class CatalogModelRuntimeAdapter implements ICatalogModelRuntimePort {
    private final ObjectProvider<LlmModelCatalog> catalogProvider;

    public List<CatalogModel> listUserSelectableModels() {
        LlmModelCatalog catalog = catalogProvider.getIfAvailable();
        return catalog == null ? List.of() : catalog.listUserSelectableModels().stream().map(this::toModel).toList();
    }

    public void invalidateAll() {
        LlmModelCatalog catalog = catalogProvider.getIfAvailable();
        if (catalog != null) catalog.invalidateAll();
    }

    private CatalogModel toModel(LlmModelCatalogEntry entry) {
        return CatalogModel.builder().modelId(entry.getModelId()).modelName(entry.getModelName())
                .modelType(entry.getModelType()).supportsThinking(entry.getSupportsThinking())
                .contextWindow(entry.getContextWindow()).status(entry.getStatus()).build();
    }
}
