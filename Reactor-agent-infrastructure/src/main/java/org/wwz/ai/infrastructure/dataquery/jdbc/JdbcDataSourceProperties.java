package org.wwz.ai.infrastructure.dataquery.jdbc;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** JDBC connection settings used by Data Query adapters and catalog providers. */
@Data
@Component
@ConfigurationProperties(prefix = "autobots.data-agent.db-config")
public class JdbcDataSourceProperties {
    private String type;
    private String url;
    private String host;
    private int port;
    private String schema;
    private String username;
    private String password;
    private String key = "reactor-datasource";
}
