package org.wwz.ai.test.domain.dataagent;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.domain.agent.reactor.config.data.DataAgentConfig;
import org.wwz.ai.domain.agent.reactor.config.data.QdrantConfig;
import org.wwz.ai.domain.agent.reactor.service.EmbeddingService;

import java.util.List;

/**
 * Embedding 代理解析测试。
 */
public class EmbeddingServiceProxyTest {

    @Test
    public void shouldResolveDefaultEmbeddingProxyUrlFromAgentUrl() {
        DataAgentConfig dataAgentConfig = new DataAgentConfig();
        dataAgentConfig.setAgentUrl("http://127.0.0.1:1601");
        dataAgentConfig.setQdrantConfig(new QdrantConfig());
        EmbeddingService embeddingService = new EmbeddingService();
        ReflectionTestUtils.setField(embeddingService, "dataAgentConfig", dataAgentConfig);

        Assert.assertEquals("http://127.0.0.1:1601/v1/tool/embedding/text", embeddingService.resolveEmbeddingUrl());
    }

    @Test
    public void shouldKeepLegacyEmbeddingOverrideAndParseObjectResponse() {
        DataAgentConfig dataAgentConfig = new DataAgentConfig();
        QdrantConfig qdrantConfig = new QdrantConfig();
        qdrantConfig.setEmbeddingUrl("http://legacy-embedding.local");
        dataAgentConfig.setQdrantConfig(qdrantConfig);
        EmbeddingService embeddingService = new EmbeddingService();
        ReflectionTestUtils.setField(embeddingService, "dataAgentConfig", dataAgentConfig);

        Assert.assertEquals("http://legacy-embedding.local", embeddingService.resolveEmbeddingUrl());
        List<List<Float>> vectors = embeddingService.parseEmbeddingResponse("""
                {"vectors":[[0.1,0.2],[0.3,0.4]],"dimension":2,"model":"text-embedding-v4"}
                """);
        Assert.assertEquals(2, vectors.size());
        Assert.assertEquals(Float.valueOf(0.1F), vectors.get(0).get(0));
        Assert.assertEquals(Float.valueOf(0.4F), vectors.get(1).get(1));
    }
}
