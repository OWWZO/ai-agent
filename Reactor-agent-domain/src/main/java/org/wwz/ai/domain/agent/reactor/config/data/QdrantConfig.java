package org.wwz.ai.domain.agent.reactor.config.data;

import lombok.Data;

@Data
/**
 * 延期保留的 Qdrant 向量库连接配置。
 */
public class QdrantConfig {
    private Boolean enable;
    private String url;
    private String host;
    private Integer port;
    private String apiKey;
    private String embeddingUrl;
    private Boolean preferGrpc;
}
