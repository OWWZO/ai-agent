package org.wwz.ai.test.domain.dataagent;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpPort;
import org.wwz.ai.domain.agent.rag.model.embedding.TextEmbeddingRequest;
import org.wwz.ai.domain.agent.rag.model.embedding.TextEmbeddingResult;
import org.wwz.ai.infrastructure.dataquery.embedding.DataQueryEmbeddingProperties;
import org.wwz.ai.infrastructure.dataquery.embedding.HttpTextEmbeddingAdapter;

import java.util.List;

/**
 * Embedding adapter URL and response compatibility tests.
 */
public class EmbeddingServiceProxyTest {

    @Test
    public void shouldResolveDefaultEmbeddingProxyUrlFromAgentUrl() {
        DataQueryEmbeddingProperties config = new DataQueryEmbeddingProperties();
        config.setAgentUrl("http://127.0.0.1:1601");
        RemoteHttpPort httpPort = request -> {
            Assert.assertEquals("http://127.0.0.1:1601/v1/tool/embedding/text", request.getUrl());
            return "[[0.1,0.2]]";
        };

        TextEmbeddingResult result = new HttpTextEmbeddingAdapter(config, httpPort)
                .embed(new TextEmbeddingRequest(List.of("hello"), true));

        Assert.assertEquals(List.of(0.1F, 0.2F), result.getVectors().get(0));
    }

    @Test
    public void shouldKeepLegacyEmbeddingOverrideAndParseObjectResponse() {
        DataQueryEmbeddingProperties config = new DataQueryEmbeddingProperties();
        DataQueryEmbeddingProperties.EmbeddingProxyProperties qdrantConfig =
                new DataQueryEmbeddingProperties.EmbeddingProxyProperties();
        qdrantConfig.setEmbeddingUrl("http://legacy-embedding.local");
        config.setQdrantConfig(qdrantConfig);
        RemoteHttpPort httpPort = request -> {
            Assert.assertEquals("http://legacy-embedding.local", request.getUrl());
            return "{\"vectors\":[[0.1,0.2],[0.3,0.4]],\"dimension\":2}";
        };

        TextEmbeddingResult result = new HttpTextEmbeddingAdapter(config, httpPort)
                .embed(new TextEmbeddingRequest(List.of("hello", "world"), true));

        Assert.assertEquals(2, result.getVectors().size());
        Assert.assertEquals(Float.valueOf(0.1F), result.getVectors().get(0).get(0));
        Assert.assertEquals(Float.valueOf(0.4F), result.getVectors().get(1).get(1));
    }
}
