package org.wwz.ai.test.domain.dataagent;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.domain.agent.reactor.config.data.DataAgentConfig;
import org.wwz.ai.domain.agent.reactor.config.data.QdrantConfig;
import org.wwz.ai.domain.agent.reactor.service.EmbeddingService;
import org.wwz.ai.domain.agent.reactor.service.QdrantService;

/**
 * 旧配置兼容测试。
 */
public class LegacyVectorCompatTest {

    @Test
    public void shouldKeepLegacyHostPortQdrantCompatibility() {
        QdrantConfig qdrantConfig = new QdrantConfig();
        qdrantConfig.setHost("10.0.0.1");
        qdrantConfig.setPort(6334);
        qdrantConfig.setApiKey("legacy-key");
        DataAgentConfig dataAgentConfig = new DataAgentConfig();
        dataAgentConfig.setQdrantConfig(qdrantConfig);

        QdrantService qdrantService = new QdrantService();
        qdrantService.setDataAgentConfig(dataAgentConfig);
        QdrantService.ResolvedQdrantEndpoint endpoint = qdrantService.resolveEndpoint(qdrantConfig);

        Assert.assertEquals("10.0.0.1", endpoint.getHost());
        Assert.assertEquals("legacy-key", endpoint.getApiKey());
        Assert.assertFalse(endpoint.isTlsEnabled());
    }

    @Test
    public void shouldKeepLegacyEmbeddingUrlCompatibility() {
        DataAgentConfig dataAgentConfig = new DataAgentConfig();
        dataAgentConfig.setAgentUrl("http://127.0.0.1:1601");
        QdrantConfig qdrantConfig = new QdrantConfig();
        qdrantConfig.setEmbeddingUrl("http://legacy.local/embedding");
        dataAgentConfig.setQdrantConfig(qdrantConfig);
        EmbeddingService embeddingService = new EmbeddingService();
        ReflectionTestUtils.setField(embeddingService, "dataAgentConfig", dataAgentConfig);

        Assert.assertEquals("http://legacy.local/embedding", embeddingService.resolveEmbeddingUrl());
    }

    @Test
    public void shouldPreferSharedCloudUrlWhenLegacyHostAlsoExists() {
        QdrantConfig qdrantConfig = new QdrantConfig();
        qdrantConfig.setUrl("https://shared-qdrant.example.com");
        qdrantConfig.setHost("10.0.0.1");
        qdrantConfig.setPort(6334);
        qdrantConfig.setApiKey("shared-key");
        DataAgentConfig dataAgentConfig = new DataAgentConfig();
        dataAgentConfig.setQdrantConfig(qdrantConfig);

        QdrantService qdrantService = new QdrantService();
        qdrantService.setDataAgentConfig(dataAgentConfig);
        QdrantService.ResolvedQdrantEndpoint endpoint = qdrantService.resolveEndpoint(qdrantConfig);

        Assert.assertEquals("shared-qdrant.example.com", endpoint.getHost());
        Assert.assertEquals("https://shared-qdrant.example.com", endpoint.getUrl());
        Assert.assertTrue(endpoint.isTlsEnabled());
    }
}
