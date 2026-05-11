package org.wwz.ai.infrastructure.dataquery.util;


import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.reactor.config.data.DbConfig;
import org.wwz.ai.infrastructure.dataquery.jdbc.JdbcConnectionConfig;
import org.wwz.ai.infrastructure.dataquery.jdbc.dialect.DialectEnum;

public class JdbcUtils {

    public static JdbcConnectionConfig parseJdbcConnectionConfig(DbConfig dbConfig) {
        if (dbConfig == null) {
            throw new IllegalArgumentException("dbConfig cannot be null");
        }
        JdbcConnectionConfig jdbcConnectionConfig = new JdbcConnectionConfig();
        jdbcConnectionConfig.setUrl(resolveJdbcUrl(dbConfig));
        jdbcConnectionConfig.setKey(dbConfig.getKey());
        jdbcConnectionConfig.setUserName(dbConfig.getUsername());
        jdbcConnectionConfig.setPassword(dbConfig.getPassword());
        jdbcConnectionConfig.setDataSourceType(dbConfig.getType());
        return jdbcConnectionConfig;
    }

    /**
     * 优先使用完整 JDBC URL，兼容独立问数库配置。
     */
    private static String resolveJdbcUrl(DbConfig dbConfig) {
        if (StringUtils.isNotBlank(dbConfig.getUrl())) {
            return dbConfig.getUrl().trim();
        }
        return createJdbcUrl(dbConfig.getType(), dbConfig.getHost(), dbConfig.getPort(), dbConfig.getSchema());
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


