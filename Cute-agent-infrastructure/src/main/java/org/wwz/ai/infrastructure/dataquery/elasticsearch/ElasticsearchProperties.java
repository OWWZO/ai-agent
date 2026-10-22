package org.wwz.ai.infrastructure.dataquery.elasticsearch;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Elasticsearch connection settings owned by Infrastructure. */
@Data
@Component
@ConfigurationProperties(prefix = "autobots.data-agent.es-config")
public class ElasticsearchProperties {

    private Boolean enable;
    private String host;
    private String user;
    private String password;
    private String apiKey;
    private String scheme;
}
