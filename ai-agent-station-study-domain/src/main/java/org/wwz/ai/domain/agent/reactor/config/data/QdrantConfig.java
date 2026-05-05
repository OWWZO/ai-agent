package org.wwz.ai.domain.agent.reactor.config.data;

import lombok.Data;

@Data
public class QdrantConfig {
    private Boolean enable;
    private String url;
    private String host;
    private Integer port;
    private String apiKey;
    private String embeddingUrl;
    private Boolean preferGrpc;
}
