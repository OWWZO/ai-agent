package org.wwz.ai.config.reactor.startup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;
import org.wwz.ai.infrastructure.dataquery.jdbc.JdbcDataSourceProperties;
import org.wwz.ai.infrastructure.dataquery.jdbc.connection.ConnectionWrapper;
import org.wwz.ai.infrastructure.dataquery.jdbc.connection.JdbcConnectionFactory;
import org.wwz.ai.infrastructure.dataquery.util.JdbcUtils;

import java.sql.Connection;

/**
 * App startup adapter for the optional H2 schema and seed scripts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class H2SchemaBootstrap {

    private final JdbcDataSourceProperties jdbcDataSourceProperties;

    public void initializeIfConfigured() {
        if (!"h2".equalsIgnoreCase(jdbcDataSourceProperties.getType())) {
            return;
        }
        JdbcDataSourceProperties jdbcProperties = new JdbcDataSourceProperties();
        jdbcProperties.setType(jdbcDataSourceProperties.getType());
        jdbcProperties.setUrl(jdbcDataSourceProperties.getUrl());
        jdbcProperties.setHost(jdbcDataSourceProperties.getHost());
        jdbcProperties.setPort(jdbcDataSourceProperties.getPort());
        jdbcProperties.setSchema(jdbcDataSourceProperties.getSchema());
        jdbcProperties.setUsername(jdbcDataSourceProperties.getUsername());
        jdbcProperties.setPassword(jdbcDataSourceProperties.getPassword());
        jdbcProperties.setKey(jdbcDataSourceProperties.getKey());
        try (ConnectionWrapper wrapper = JdbcConnectionFactory.getConnection(
                     JdbcUtils.parseJdbcConnectionConfig(jdbcProperties));
             Connection connection = wrapper.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/schema.sql"));
            try {
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/data.sql"));
            } catch (Exception e) {
                log.warn("Execute data.sql failed or file not found, skipping data init: {}", e.getMessage());
            }
            log.info("H2 database initialized with schema.sql");
        } catch (Exception e) {
            log.error("Failed to initialize H2 database", e);
        }
    }
}
