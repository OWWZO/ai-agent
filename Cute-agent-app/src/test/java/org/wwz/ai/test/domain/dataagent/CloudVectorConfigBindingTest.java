package org.wwz.ai.test.domain.dataagent;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.rag.model.config.ColumnValueRecallSettings;
import org.wwz.ai.domain.agent.rag.model.config.DataQuerySettings;
import org.wwz.ai.domain.agent.rag.model.config.VectorRecallSettings;
import org.wwz.ai.infrastructure.dataquery.elasticsearch.ElasticsearchProperties;
import org.wwz.ai.infrastructure.dataquery.vector.QdrantProperties;

/**
 * 共享云端向量配置绑定测试。
 */
public class CloudVectorConfigBindingTest {

    @Test
    public void shouldKeepSharedCloudFields() {
        DataQuerySettings dataAgentConfig = new DataQuerySettings();
        VectorRecallSettings qdrantConfig = new VectorRecallSettings();
        ColumnValueRecallSettings esConfig = new ColumnValueRecallSettings();
        QdrantProperties qdrantProperties = new QdrantProperties();
        ElasticsearchProperties elasticsearchProperties = new ElasticsearchProperties();

        dataAgentConfig.setAgentUrl("http://127.0.0.1:1601");
        dataAgentConfig.setForceRefresh(true);
        qdrantConfig.setEnable(true);
        qdrantProperties.setUrl("https://qdrant.example.com");
        qdrantProperties.setPort(6334);
        qdrantProperties.setPreferGrpc(true);
        qdrantProperties.setApiKey("qdrant-key");
        esConfig.setEnable(true);
        elasticsearchProperties.setScheme("https");
        elasticsearchProperties.setHost("es.example.com:9200");
        elasticsearchProperties.setApiKey("es-api-key");
        dataAgentConfig.setQdrantConfig(qdrantConfig);
        dataAgentConfig.setEsConfig(esConfig);

        Assert.assertEquals("http://127.0.0.1:1601", dataAgentConfig.getAgentUrl());
        Assert.assertTrue(dataAgentConfig.getForceRefresh());
        Assert.assertEquals("https://qdrant.example.com", qdrantProperties.getUrl());
        Assert.assertEquals(Integer.valueOf(6334), qdrantProperties.getPort());
        Assert.assertTrue(qdrantProperties.getPreferGrpc());
        Assert.assertEquals("https", elasticsearchProperties.getScheme());
        Assert.assertEquals("es-api-key", elasticsearchProperties.getApiKey());
    }

    @Test
    public void shouldKeepExplicitConfigOnly() {
        VectorRecallSettings qdrantConfig = new VectorRecallSettings();
        ColumnValueRecallSettings esConfig = new ColumnValueRecallSettings();
        DataQuerySettings dataAgentConfig = new DataQuerySettings();

        Assert.assertNull(qdrantConfig.getEnable());
        QdrantProperties qdrantProperties = new QdrantProperties();
        ElasticsearchProperties elasticsearchProperties = new ElasticsearchProperties();
        Assert.assertNull(qdrantProperties.getPort());
        Assert.assertNull(qdrantProperties.getPreferGrpc());
        Assert.assertNull(esConfig.getEnable());
        Assert.assertNull(elasticsearchProperties.getScheme());
        Assert.assertFalse(dataAgentConfig.getForceRefresh());
        Assert.assertNotNull(dataAgentConfig.getQdrantConfig());
        Assert.assertNotNull(dataAgentConfig.getEsConfig());
        Assert.assertNotNull(dataAgentConfig.getDbConfig());
        Assert.assertNull(dataAgentConfig.getQdrantConfig().getEnable());
        Assert.assertNull(dataAgentConfig.getEsConfig().getEnable());
    }
}
