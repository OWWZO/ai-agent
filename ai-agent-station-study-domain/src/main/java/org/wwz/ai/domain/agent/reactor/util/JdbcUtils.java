package org.wwz.ai.domain.agent.reactor.util;


import org.wwz.ai.domain.agent.reactor.config.data.DbConfig;
import org.wwz.ai.domain.agent.reactor.data.jdbc.JdbcConnectionConfig;
import org.wwz.ai.domain.agent.reactor.data.jdbc.dialect.DialectEnum;

public class JdbcUtils {

    public static JdbcConnectionConfig parseJdbcConnectionConfig(DbConfig dbConfig) {
        if (dbConfig == null) {
            throw new IllegalArgumentException("dbConfig cannot be null");
        }
        JdbcConnectionConfig jdbcConnectionConfig = new JdbcConnectionConfig();
        jdbcConnectionConfig.setUrl(createJdbcUrl(dbConfig.getType(), dbConfig.getHost(), dbConfig.getPort(), dbConfig.getSchema()));
        jdbcConnectionConfig.setKey(dbConfig.getKey());
        jdbcConnectionConfig.setUserName(dbConfig.getUsername());
        jdbcConnectionConfig.setPassword(dbConfig.getPassword());
        jdbcConnectionConfig.setDataSourceType(dbConfig.getType());
        return jdbcConnectionConfig;
    }

    public static String createJdbcUrl(String type, String host, int port, String schemaName) {
        DialectEnum dialectEnum = DialectEnum.of(type);
        String base = dialectEnum.getUrlPrefix() + host;
        if (port > 0) {
            return base + ":" + port + dialectEnum.getSuffixDelimiter() + schemaName + dialectEnum.getUrlEndWith();
        }
        return dialectEnum.getUrlPrefix() + host + dialectEnum.getSuffixDelimiter() + schemaName + dialectEnum.getUrlEndWith();
    }

}
