package org.wwz.ai.domain.agent.rag;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.rag.model.column.ColumnValueDocument;
import org.wwz.ai.domain.agent.rag.model.column.ColumnValueHit;
import org.wwz.ai.domain.agent.rag.model.column.ColumnValueSearchRequest;
import org.wwz.ai.domain.agent.rag.model.column.ColumnValueSearchResult;
import org.wwz.ai.domain.agent.rag.model.embedding.TextEmbeddingRequest;
import org.wwz.ai.domain.agent.rag.model.embedding.TextEmbeddingResult;
import org.wwz.ai.domain.agent.rag.model.vector.VectorFilter;
import org.wwz.ai.domain.agent.rag.model.vector.VectorPoint;
import org.wwz.ai.domain.agent.rag.model.vector.VectorSearchHit;
import org.wwz.ai.domain.agent.rag.model.vector.VectorSearchRequest;
import org.wwz.ai.domain.agent.rag.model.vector.VectorSearchResult;
import org.wwz.ai.domain.agent.rag.port.ColumnValueIndexAdminPort;
import org.wwz.ai.domain.agent.rag.port.ColumnValueSearchPort;
import org.wwz.ai.domain.agent.rag.port.TextEmbeddingPort;
import org.wwz.ai.domain.agent.rag.port.VectorIndexAdminPort;
import org.wwz.ai.domain.agent.rag.port.VectorSearchPort;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * RAG Port 契约边界测试。
 */
public class RagPortContractTest {

    @Test
    public void portsMustNotExposeInfrastructureClientTypes() {
        Class<?>[] ports = {
                TextEmbeddingPort.class,
                VectorSearchPort.class,
                VectorIndexAdminPort.class,
                ColumnValueSearchPort.class,
                ColumnValueIndexAdminPort.class
        };
        for (Class<?> port : ports) {
            for (Method method : port.getDeclaredMethods()) {
                String signature = method.toGenericString();
                Assert.assertFalse(signature, signature.contains("io.qdrant.client"));
                Assert.assertFalse(signature, signature.contains("org.elasticsearch"));
                Assert.assertFalse(signature, signature.contains("okhttp3"));
                Assert.assertFalse(signature, signature.contains("RestHighLevelClient"));
                Assert.assertFalse(signature, signature.contains("org.elasticsearch.action.search.SearchRequest"));
                Assert.assertFalse(signature, signature.contains("org.elasticsearch.search.SearchHit"));
                Assert.assertFalse(signature, signature.contains("io.qdrant.client.grpc.Points"));
            }
        }
    }

    @Test
    public void portsAreGroupedByRagCapability() {
        Assert.assertEquals("TextEmbeddingPort", TextEmbeddingPort.class.getSimpleName());
        Assert.assertEquals("VectorSearchPort", VectorSearchPort.class.getSimpleName());
        Assert.assertEquals("VectorIndexAdminPort", VectorIndexAdminPort.class.getSimpleName());
        Assert.assertEquals("ColumnValueSearchPort", ColumnValueSearchPort.class.getSimpleName());
        Assert.assertEquals("ColumnValueIndexAdminPort", ColumnValueIndexAdminPort.class.getSimpleName());
        Assert.assertTrue(Arrays.stream(TextEmbeddingPort.class.getPackageName().split("\\."))
                .anyMatch("rag"::equals));
    }

    @Test
    public void ragModelsNormalizeNullCollectionsAndCopyInputs() {
        java.util.List<Float> vector = new java.util.ArrayList<>(java.util.List.of(0.1F));
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("modelCode", "sales");

        VectorPoint point = new VectorPoint("point-1", vector, payload);
        VectorSearchRequest searchRequest = new VectorSearchRequest(
                "schema", vector, 10, 0.5F, payload, java.util.List.of("modelCode"), 1000L);
        VectorFilter filter = new VectorFilter(payload);
        ColumnValueDocument document = new ColumnValueDocument("value-1", payload);
        ColumnValueSearchRequest columnRequest = new ColumnValueSearchRequest(
                "Shanghai", java.util.List.of("sales"), 10);
        TextEmbeddingRequest embeddingRequest = new TextEmbeddingRequest(
                java.util.List.of("hello"), true);
        TextEmbeddingResult embeddingResult = TextEmbeddingResult.available(
                java.util.List.of(java.util.List.of(0.1F, 0.2F)));

        vector.add(0.2F);
        payload.put("changed", true);

        Assert.assertEquals(1, point.getVector().size());
        Assert.assertFalse(point.getPayload().containsKey("changed"));
        Assert.assertFalse(searchRequest.getFilters().containsKey("changed"));
        Assert.assertFalse(filter.getMust().containsKey("changed"));
        Assert.assertEquals(1, document.getFields().size());
        Assert.assertEquals(java.util.List.of("sales"), columnRequest.getModelCodes());
        Assert.assertEquals(java.util.List.of("hello"), embeddingRequest.getTexts());
        Assert.assertEquals(java.util.List.of(0.1F, 0.2F), embeddingResult.getVectors().get(0));

        Assert.assertTrue(new VectorSearchHit("empty", 0F, null).getPayload().isEmpty());
        Assert.assertTrue(new VectorSearchResult(null).getHits().isEmpty());
        Assert.assertTrue(new ColumnValueHit(null, null).getFields().isEmpty());
        Assert.assertTrue(new ColumnValueSearchResult(null).getHits().isEmpty());
        Assert.assertTrue(new TextEmbeddingRequest(null, true).getTexts().isEmpty());
    }
}
