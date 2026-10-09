package org.wwz.ai.infrastructure.dataquery.embedding;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Embedding proxy endpoint settings. */
@Data
@Component
@ConfigurationProperties(prefix = "autobots.data-agent")
public class DataQueryEmbeddingProperties {
    private String agentUrl;
    private EmbeddingProxyProperties qdrantConfig = new EmbeddingProxyProperties();

    @Data
    public static class EmbeddingProxyProperties {
        private String embeddingUrl;
    }
}
