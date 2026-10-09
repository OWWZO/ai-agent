package org.wwz.ai.infrastructure.dataquery.vector;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Qdrant endpoint and authentication settings. */
@Data
@Component
@ConfigurationProperties(prefix = "autobots.data-agent.qdrant-config")
public class QdrantProperties {
    private Boolean enable;
    private String url;
    private String host;
    private Integer port;
    private String apiKey;
    private String embeddingUrl;
    private Boolean preferGrpc;
}
