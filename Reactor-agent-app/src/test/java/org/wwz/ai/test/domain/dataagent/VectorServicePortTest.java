package org.wwz.ai.test.domain.dataagent;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.rag.model.embedding.TextEmbeddingResult;
import org.wwz.ai.domain.agent.rag.model.vector.VectorRecallRequest;
import org.wwz.ai.domain.agent.rag.model.vector.VectorSearchHit;
import org.wwz.ai.domain.agent.rag.model.vector.VectorSearchResult;
import org.wwz.ai.domain.agent.rag.port.TextEmbeddingPort;
import org.wwz.ai.domain.agent.rag.port.VectorSearchPort;
import org.wwz.ai.domain.agent.rag.service.VectorService;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * 向量领域服务 Port 编排测试。
 */
public class VectorServicePortTest {

    @Test
    public void shouldMapPortHitToLegacyRecallResult() {
        VectorService service = new VectorService();
        TextEmbeddingPort embeddingPort = request -> TextEmbeddingResult.available(
                List.of(List.of(0.1F, 0.2F)));
        VectorSearchPort searchPort = request -> new VectorSearchResult(List.of(
                new VectorSearchHit("point-1", 0.91F, Map.of("modelCode", "sales"))));
        Executor directExecutor = runnable -> runnable.run();
        service.setTextEmbeddingPort(embeddingPort);
        service.setVectorSearchPort(searchPort);
        service.setToolExecutor(directExecutor);

        VectorRecallRequest request = new VectorRecallRequest(
                "revenue", "reactor_model_schema", 100, 0.5F, Map.of(), List.of(), 1000L);

        List<Map<String, Object>> result = service.vectorRecall(request);

        Assert.assertEquals(1, result.size());
        Assert.assertEquals("sales", result.get(0).get("modelCode"));
        Assert.assertEquals("point-1", result.get(0).get("_id"));
        Assert.assertEquals(0.91F, result.get(0).get("score"));
    }

    @Test
    public void shouldReturnEmptyWhenEmbeddingPortIsUnavailable() {
        VectorService service = new VectorService();
        TextEmbeddingPort unavailableEmbeddingPort = request -> TextEmbeddingResult.unavailable();
        VectorSearchPort unavailableSearchPort = request -> {
            throw new AssertionError("search must not run when embedding is unavailable");
        };
        Executor directExecutor = runnable -> runnable.run();
        service.setTextEmbeddingPort(unavailableEmbeddingPort);
        service.setVectorSearchPort(unavailableSearchPort);
        service.setToolExecutor(directExecutor);

        VectorRecallRequest request = new VectorRecallRequest(
                "revenue", "reactor_model_schema", 100, 0.5F, Map.of(), List.of(), 1000L);

        Assert.assertTrue(service.vectorRecall(request).isEmpty());
    }
}
