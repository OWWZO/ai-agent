package org.wwz.ai.test.domain.dataagent;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.reactor.config.data.DataAgentConfig;
import org.wwz.ai.domain.agent.reactor.config.data.QdrantConfig;
import org.wwz.ai.domain.agent.reactor.service.QdrantService;

/**
 * Qdrant 云端地址解析测试。
 */
public class QdrantServiceCloudClientTest {

    @Test
    public void shouldResolveHttpsUrlToTlsEndpoint() {
        QdrantConfig qdrantConfig = new QdrantConfig();
        qdrantConfig.setUrl("https://cluster.qdrant.cloud");
        qdrantConfig.setPort(6334);
        qdrantConfig.setApiKey("key");
        qdrantConfig.setPreferGrpc(true);
        DataAgentConfig dataAgentConfig = new DataAgentConfig();
        dataAgentConfig.setQdrantConfig(qdrantConfig);

        QdrantService service = new QdrantService();
        service.setDataAgentConfig(dataAgentConfig);

        QdrantService.ResolvedQdrantEndpoint endpoint = service.resolveEndpoint(qdrantConfig);
        Assert.assertEquals("cluster.qdrant.cloud", endpoint.getHost());
        Assert.assertEquals(6334, endpoint.getPort());
        Assert.assertTrue(endpoint.isTlsEnabled());
        Assert.assertEquals("key", endpoint.getApiKey());
        Assert.assertTrue(endpoint.isPreferGrpc());
    }

    @Test
    public void shouldKeepLegacyHostPortMode() {
        QdrantConfig qdrantConfig = new QdrantConfig();
        qdrantConfig.setHost("127.0.0.1");
        qdrantConfig.setPort(6334);
        qdrantConfig.setPreferGrpc(false);
        DataAgentConfig dataAgentConfig = new DataAgentConfig();
        dataAgentConfig.setQdrantConfig(qdrantConfig);

        QdrantService service = new QdrantService();
        service.setDataAgentConfig(dataAgentConfig);

        QdrantService.ResolvedQdrantEndpoint endpoint = service.resolveEndpoint(qdrantConfig);
        Assert.assertEquals("127.0.0.1", endpoint.getHost());
        Assert.assertEquals(6334, endpoint.getPort());
        Assert.assertFalse(endpoint.isTlsEnabled());
        Assert.assertFalse(endpoint.isPreferGrpc());
    }
}
