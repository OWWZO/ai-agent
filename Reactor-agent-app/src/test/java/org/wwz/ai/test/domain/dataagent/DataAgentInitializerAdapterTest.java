package org.wwz.ai.test.domain.dataagent;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.wwz.ai.domain.agent.rag.model.embedding.TextEmbeddingResult;
import org.wwz.ai.domain.agent.rag.port.ColumnValueIndexAdminPort;
import org.wwz.ai.domain.agent.rag.port.TextEmbeddingPort;
import org.wwz.ai.domain.agent.rag.port.VectorIndexAdminPort;
import org.wwz.ai.infrastructure.dataquery.vector.VectorDataQueryDefaults;
import org.wwz.ai.infrastructure.dataquery.vector.DataAgentInitializerAdapter;

import java.util.List;

public class DataAgentInitializerAdapterTest {

    @Test
    public void shouldPrepareIndexesThroughExistingRagPorts() throws Exception {
        TextEmbeddingPort embeddingPort = Mockito.mock(TextEmbeddingPort.class);
        Mockito.when(embeddingPort.embed(Mockito.any()))
                .thenReturn(TextEmbeddingResult.available(List.of(List.of(0.25f))));
        VectorIndexAdminPort vectorIndexPort = Mockito.mock(VectorIndexAdminPort.class);
        ColumnValueIndexAdminPort columnValueIndexPort = Mockito.mock(ColumnValueIndexAdminPort.class);
        Mockito.when(columnValueIndexPort.isAvailable()).thenReturn(true);
        DataAgentInitializerAdapter adapter = new DataAgentInitializerAdapter(
                embeddingPort, vectorIndexPort, columnValueIndexPort);

        adapter.initializeVectorIndex(true, 768);
        adapter.initializeColumnValueIndex(true);

        Mockito.verify(vectorIndexPort).recreateCollection(VectorDataQueryDefaults.SCHEMA_COLLECTION_NAME, 768);
        Mockito.verify(columnValueIndexPort).recreateIndex();
    }

    @Test
    public void shouldRejectVectorInitializationWhenEmbeddingIsUnavailable() throws Exception {
        TextEmbeddingPort embeddingPort = Mockito.mock(TextEmbeddingPort.class);
        Mockito.when(embeddingPort.embed(Mockito.any())).thenReturn(TextEmbeddingResult.unavailable());
        VectorIndexAdminPort vectorIndexPort = Mockito.mock(VectorIndexAdminPort.class);
        ColumnValueIndexAdminPort columnValueIndexPort = Mockito.mock(ColumnValueIndexAdminPort.class);
        DataAgentInitializerAdapter adapter = new DataAgentInitializerAdapter(
                embeddingPort, vectorIndexPort, columnValueIndexPort);

        IllegalStateException exception = Assert.assertThrows(IllegalStateException.class,
                () -> adapter.initializeVectorIndex(false, 1024));

        Assert.assertEquals("共享文本向量代理不可用", exception.getMessage());
        Mockito.verifyNoInteractions(vectorIndexPort);
    }

    @Test
    public void shouldRejectColumnIndexInitializationWhenIndexIsUnavailable() {
        TextEmbeddingPort embeddingPort = Mockito.mock(TextEmbeddingPort.class);
        VectorIndexAdminPort vectorIndexPort = Mockito.mock(VectorIndexAdminPort.class);
        ColumnValueIndexAdminPort columnValueIndexPort = Mockito.mock(ColumnValueIndexAdminPort.class);
        DataAgentInitializerAdapter adapter = new DataAgentInitializerAdapter(
                embeddingPort, vectorIndexPort, columnValueIndexPort);

        IllegalStateException exception = Assert.assertThrows(IllegalStateException.class,
                () -> adapter.initializeColumnValueIndex(false));

        Assert.assertEquals("column-value index is unavailable", exception.getMessage());
        Mockito.verify(columnValueIndexPort, Mockito.never()).initializeIndex();
    }
}
