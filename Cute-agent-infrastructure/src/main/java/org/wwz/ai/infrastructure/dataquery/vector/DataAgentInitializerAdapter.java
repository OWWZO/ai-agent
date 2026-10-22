package org.wwz.ai.infrastructure.dataquery.vector;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.rag.model.embedding.TextEmbeddingRequest;
import org.wwz.ai.domain.agent.rag.model.embedding.TextEmbeddingResult;
import org.wwz.ai.domain.agent.rag.port.ColumnValueIndexAdminPort;
import org.wwz.ai.domain.agent.rag.port.DataAgentInitializerPort;
import org.wwz.ai.domain.agent.rag.port.TextEmbeddingPort;
import org.wwz.ai.domain.agent.rag.port.VectorIndexAdminPort;

import java.util.List;

/**
 * Infrastructure adapter for preparing DataAgent's optional vector and column-value indexes.
 */
@Component
@RequiredArgsConstructor
public class DataAgentInitializerAdapter implements DataAgentInitializerPort {

    private final TextEmbeddingPort textEmbeddingPort;
    private final VectorIndexAdminPort vectorIndexAdminPort;
    private final ColumnValueIndexAdminPort columnValueIndexAdminPort;

    @Override
    public void initializeVectorIndex(boolean forceRefresh, int embeddingDimension) throws Exception {
        TextEmbeddingResult healthCheck = textEmbeddingPort.embed(
                new TextEmbeddingRequest(List.of("health_check"), true));
        if (healthCheck == null || !healthCheck.isAvailable() || healthCheck.getVectors().isEmpty()
                || healthCheck.getVectors().get(0).isEmpty()) {
            throw new IllegalStateException("共享文本向量代理不可用");
        }
        if (forceRefresh) {
            vectorIndexAdminPort.recreateCollection(VectorDataQueryDefaults.SCHEMA_COLLECTION_NAME, embeddingDimension);
        } else {
            vectorIndexAdminPort.createCollection(VectorDataQueryDefaults.SCHEMA_COLLECTION_NAME, embeddingDimension);
        }
    }

    @Override
    public void initializeColumnValueIndex(boolean forceRefresh) {
        if (!columnValueIndexAdminPort.isAvailable()) {
            throw new IllegalStateException("column-value index is unavailable");
        }
        if (forceRefresh) {
            columnValueIndexAdminPort.recreateIndex();
        } else {
            columnValueIndexAdminPort.initializeIndex();
        }
    }
}
