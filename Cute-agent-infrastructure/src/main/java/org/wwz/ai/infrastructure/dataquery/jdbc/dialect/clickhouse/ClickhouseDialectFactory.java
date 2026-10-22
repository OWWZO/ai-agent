package org.wwz.ai.infrastructure.dataquery.jdbc.dialect.clickhouse;


import com.google.auto.service.AutoService;
import org.wwz.ai.infrastructure.dataquery.jdbc.dialect.JdbcDialect;
import org.wwz.ai.infrastructure.dataquery.jdbc.dialect.JdbcDialectFactory;


/** 通过 ClickHouse JDBC URL 前缀注册 ClickHouse 方言。 */
@AutoService(JdbcDialectFactory.class)
public class ClickhouseDialectFactory implements JdbcDialectFactory {
    @Override
    public boolean acceptsURL(String url) {
        // 同时兼容旧版 jdbc:ch 和官方 jdbc:clickhouse 前缀。
        return url.startsWith("jdbc:ch:") || url.startsWith("jdbc:clickhouse:");
    }

    @Override
    public JdbcDialect create() {
        return new ClickhouseDialect();
    }
}
