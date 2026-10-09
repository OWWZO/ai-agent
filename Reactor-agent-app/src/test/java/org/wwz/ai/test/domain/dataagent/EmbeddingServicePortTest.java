package org.wwz.ai.test.domain.dataagent;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.rag.model.embedding.TextEmbeddingResult;
import org.wwz.ai.domain.agent.rag.port.TextEmbeddingPort;
import org.wwz.ai.domain.agent.rag.service.TextEmbeddingService;

import java.util.List;

/**
 * embedding 领域服务 Port 委托测试。
 */
public class EmbeddingServicePortTest {

    @Test
    public void shouldDelegateVectorizationToPort() {
        TextEmbeddingService service = new TextEmbeddingService();
        TextEmbeddingPort port = request -> TextEmbeddingResult.available(
                List.of(List.of(0.3F, 0.4F)));
        service.setTextEmbeddingPort(port);

        Assert.assertEquals(List.of(0.3F, 0.4F), service.getVector("hello"));
        Assert.assertTrue(service.healthCheck());
    }
}
