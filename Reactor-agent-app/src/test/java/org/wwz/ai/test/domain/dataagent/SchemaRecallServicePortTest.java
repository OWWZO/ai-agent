package org.wwz.ai.test.domain.dataagent;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.rag.service.SchemaRecallService;
import org.wwz.ai.domain.agent.rag.model.column.ColumnValueHit;
import org.wwz.ai.domain.agent.rag.model.column.ColumnValueSearchRequest;
import org.wwz.ai.domain.agent.rag.model.column.ColumnValueSearchResult;
import org.wwz.ai.domain.agent.rag.port.ColumnValueSearchPort;
import org.wwz.ai.domain.agent.rag.model.embedding.TextEmbeddingResult;
import org.wwz.ai.domain.agent.rag.model.query.ColumnSchemaRecallQuery;
import org.wwz.ai.domain.agent.rag.model.vector.VectorSearchRequest;
import org.wwz.ai.domain.agent.rag.model.vector.VectorSearchResult;
import org.wwz.ai.domain.agent.rag.port.TextEmbeddingPort;
import org.wwz.ai.domain.agent.rag.port.VectorSearchPort;

import java.util.List;
import java.util.Map;

/**
 * 列值召回领域降级测试。
 */
public class SchemaRecallServicePortTest {

    @Test
    public void shouldMapColumnValuePortResult() {
        ColumnValueSearchPort port = request -> new ColumnValueSearchResult(List.of(
                new ColumnValueHit(Map.of("value", "Shanghai"), 0.77F)));
        SchemaRecallService service = new SchemaRecallService(null, null, port);

        ColumnValueSearchRequest request = new ColumnValueSearchRequest("Shanghai", List.of("sales"), 100);

        List<Map<String, Object>> result = service.esValueRecall(request);

        Assert.assertEquals(1, result.size());
        Assert.assertEquals("Shanghai", result.get(0).get("value"));
        Assert.assertEquals(0.77F, result.get(0).get("_score"));
    }

    @Test
    public void shouldDegradeToEmptyWhenColumnValuePortFails() {
        ColumnValueSearchPort unavailablePort = request -> {
            throw new IllegalStateException("ES unavailable");
        };
        SchemaRecallService service = new SchemaRecallService(null, null, unavailablePort);

        ColumnValueSearchRequest request = new ColumnValueSearchRequest("Shanghai", List.of("sales"), 100);

        Assert.assertTrue(service.esValueRecall(request).isEmpty());
    }

    @Test
    public void shouldDegradeToEmptyWhenVectorEmbeddingIsUnavailable() {
        TextEmbeddingPort embeddingPort = request -> TextEmbeddingResult.unavailable();
        VectorSearchPort vectorPort = request -> {
            throw new AssertionError("vector search should not run without an embedding");
        };
        SchemaRecallService service = new SchemaRecallService(embeddingPort, vectorPort, null);
        ColumnSchemaRecallQuery query = new ColumnSchemaRecallQuery("revenue", 10, 0.5F, 1000L, List.of("sales"));

        Assert.assertTrue(service.vectorRecall(query).isEmpty());
    }

    @Test
    public void preservesVectorRecallTimeoutAndModelFilterAtPortBoundary() {
        VectorSearchRequest[] captured = new VectorSearchRequest[1];
        SchemaRecallService service = new SchemaRecallService(
                request -> TextEmbeddingResult.available(List.of(List.of(0.1F))),
                request -> {
                    captured[0] = request;
                    return VectorSearchResult.empty();
                },
                null);

        service.vectorRecall(new ColumnSchemaRecallQuery("revenue", 12, 0.6F, 4321L, List.of("sales")));

        Assert.assertEquals(Long.valueOf(4321L), captured[0].getTimeoutMillis());
        Assert.assertEquals(12L, (long) captured[0].getLimit());
        Assert.assertEquals(Float.valueOf(0.6F), captured[0].getScoreThreshold());
        Assert.assertEquals(List.of("sales"), captured[0].getFilters().get("modelCode"));
    }
}
