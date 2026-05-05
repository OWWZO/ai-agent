package org.wwz.ai.test.domain.dataagent;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.reactor.config.data.DataAgentConfig;
import org.wwz.ai.domain.agent.reactor.config.data.EsConfig;
import org.wwz.ai.domain.agent.reactor.config.data.QdrantConfig;

/**
 * 共享云端向量配置绑定测试。
 */
public class CloudVectorConfigBindingTest {

    @Test
    public void shouldKeepSharedCloudFields() {
        DataAgentConfig dataAgentConfig = new DataAgentConfig();
        QdrantConfig qdrantConfig = new QdrantConfig();
        EsConfig esConfig = new EsConfig();

        dataAgentConfig.setAgentUrl("http://127.0.0.1:1601");
        dataAgentConfig.setForceRefresh(true);
        qdrantConfig.setEnable(true);
        qdrantConfig.setUrl("https://qdrant.example.com");
        qdrantConfig.setPort(6334);
        qdrantConfig.setPreferGrpc(true);
        qdrantConfig.setApiKey("qdrant-key");
        esConfig.setEnable(true);
        esConfig.setScheme("https");
        esConfig.setHost("es.example.com:9200");
        esConfig.setApiKey("es-api-key");
        dataAgentConfig.setQdrantConfig(qdrantConfig);
        dataAgentConfig.setEsConfig(esConfig);

        Assert.assertEquals("http://127.0.0.1:1601", dataAgentConfig.getAgentUrl());
        Assert.assertTrue(dataAgentConfig.getForceRefresh());
        Assert.assertEquals("https://qdrant.example.com", dataAgentConfig.getQdrantConfig().getUrl());
        Assert.assertEquals(Integer.valueOf(6334), dataAgentConfig.getQdrantConfig().getPort());
        Assert.assertTrue(dataAgentConfig.getQdrantConfig().getPreferGrpc());
        Assert.assertEquals("https", dataAgentConfig.getEsConfig().getScheme());
        Assert.assertEquals("es-api-key", dataAgentConfig.getEsConfig().getApiKey());
    }

    @Test
    public void shouldKeepExplicitConfigOnly() {
        QdrantConfig qdrantConfig = new QdrantConfig();
        EsConfig esConfig = new EsConfig();
        DataAgentConfig dataAgentConfig = new DataAgentConfig();

        Assert.assertNull(qdrantConfig.getEnable());
        Assert.assertNull(qdrantConfig.getPort());
        Assert.assertNull(qdrantConfig.getPreferGrpc());
        Assert.assertNull(esConfig.getEnable());
        Assert.assertNull(esConfig.getScheme());
        Assert.assertFalse(dataAgentConfig.getForceRefresh());
        Assert.assertNotNull(dataAgentConfig.getQdrantConfig());
        Assert.assertNotNull(dataAgentConfig.getEsConfig());
        Assert.assertNotNull(dataAgentConfig.getDbConfig());
        Assert.assertNull(dataAgentConfig.getQdrantConfig().getEnable());
        Assert.assertNull(dataAgentConfig.getEsConfig().getEnable());
    }
}
