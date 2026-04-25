package org.wwz.ai.domain.agent.reactor.config.data;

import lombok.Data;

@Data
public class QdrantConfig {
    private Boolean enable = false;
    private String url;
    private String host;
    private Integer port = 6334;
    private String apiKey;
    private String embeddingUrl;
    private Boolean preferGrpc = true;
}
