package org.wwz.ai.infrastructure.dataquery.jdbc.connection;


import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.wwz.ai.domain.agent.reactor.data.exception.JdbcBizException;
import org.wwz.ai.infrastructure.dataquery.jdbc.JdbcConnectionConfig;
import org.wwz.ai.infrastructure.dataquery.jdbc.catalog.JdbcCatalog;
import org.wwz.ai.infrastructure.dataquery.jdbc.catalog.JdbcCatalogLoader;
import org.wwz.ai.infrastructure.dataquery.jdbc.dialect.JdbcDialect;
import org.wwz.ai.infrastructure.dataquery.jdbc.dialect.JdbcDialectLoader;

import javax.sql.DataSource;
import java.io.Serializable;
import java.util.Properties;

/**
 * JDBC 连接池及其配套方言能力的组装器。
 *
 * <p>工厂先根据 URL 选择方言，再选择元数据目录，最后将连接池参数、方言默认属性和
 * 外部扩展属性合并到 Hikari 配置。支持的数据库类型在这里形成明确边界，未知方言
 * 不会降级为默认连接池。</p>
 */
@Slf4j
public class JdbcConnectionPoolFactory implements Serializable {

    private static final long serialVersionUID = 8831447217680924931L;


    public static final String CONNECTION_POOL_PREFIX = "dataAgent-connection-pool-";


    public DatasourceWrapper createPooledDatasource(JdbcConnectionConfig connConfig) {
        // 方言和目录必须来自同一份配置推导，防止 SQL 语法与元数据读取策略错配。
        JdbcDialect jdbcDialect = JdbcDialectLoader.load(connConfig.getUrl());
        DatasourceWrapper datasourceWrapper = new DatasourceWrapper();
        datasourceWrapper.setJdbcDialect(jdbcDialect);
        JdbcCatalog catalog = JdbcCatalogLoader.load(jdbcDialect.dialectName());
        datasourceWrapper.setCatalog(catalog);
        datasourceWrapper.setFreshTime(connConfig.getFreshTimestamp());

        //set jdbc dialect
        connConfig.setJdbcDialect(jdbcDialect.dialectName());
        switch (connConfig.getJdbcDialect()) {
            case MYSQL:
            case H2:
            case CLICKHOUSE:
                datasourceWrapper.setDataSource(createHikariDatasource(connConfig, jdbcDialect));
                break;
            default:
                throw new JdbcBizException(String.format("%s 暂不支持", connConfig.getJdbcDialect()));
        }
        return datasourceWrapper;
    }

    private DataSource createHikariDatasource(JdbcConnectionConfig connConfig, JdbcDialect jdbcDialect) {
        // Hikari 只负责连接池参数，方言默认属性和用户扩展属性在此统一注入。
        HikariConfig config = new HikariConfig();
        config.setPoolName(CONNECTION_POOL_PREFIX + connConfig.getKey());
        config.setDriverClassName(jdbcDialect.driverName());
        config.setUsername(connConfig.getUserName());
        config.setPassword(connConfig.getPassword());
        config.setJdbcUrl(connConfig.getUrl());

        if (connConfig.getReadOnly() != null) {
            config.setReadOnly(connConfig.getReadOnly());
        }
        if (connConfig.getConnectionTimeout() != null) {
            config.setConnectionTimeout((connConfig.getConnectionTimeout()));
        }
        if (connConfig.getIdleTimeout() != null) {
            config.setIdleTimeout(connConfig.getIdleTimeout());

        }
        if (connConfig.getMaxLifetime() != null) {
            config.setMaxLifetime(connConfig.getMaxLifetime());
        }
        if (connConfig.getMaxPoolSize() != null) {
            config.setMaximumPoolSize(connConfig.getMaxPoolSize());
        }
        if (connConfig.getMinIdle() != null) {
            config.setMinimumIdle(connConfig.getMinIdle());
        }
        if (connConfig.getKeepAliveTime() != null) {
            config.setKeepaliveTime(connConfig.getKeepAliveTime());
        }
        //方言配置
        config.setConnectionTestQuery(jdbcDialect.testSql());
        Properties properties = jdbcDialect.defaultProperties();
        if (properties != null) {
            for (String name : properties.stringPropertyNames()) {
                config.addDataSourceProperty(name, properties.getProperty(name));
            }
        }
        //jdbc 数据库配置
        Properties extConfig = connConfig.getExtConfig();
        if (extConfig != null) {
            for (String name : extConfig.stringPropertyNames()) {
                config.addDataSourceProperty(name, extConfig.getProperty(name));
            }
        }
        return new HikariDataSource(config);
    }
}
