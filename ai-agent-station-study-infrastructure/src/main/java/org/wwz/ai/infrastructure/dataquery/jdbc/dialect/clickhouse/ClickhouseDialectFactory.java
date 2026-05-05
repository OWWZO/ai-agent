package org.wwz.ai.infrastructure.dataquery.jdbc.dialect.clickhouse;


import com.google.auto.service.AutoService;
import org.wwz.ai.infrastructure.dataquery.jdbc.dialect.JdbcDialect;
import org.wwz.ai.infrastructure.dataquery.jdbc.dialect.JdbcDialectFactory;


@AutoService(JdbcDialectFactory.class)
public class ClickhouseDialectFactory implements JdbcDialectFactory {
    @Override
    public boolean acceptsURL(String url) {
        return url.startsWith("jdbc:ch:") || url.startsWith("jdbc:clickhouse:");
    }

    @Override
    public JdbcDialect create() {
        return new ClickhouseDialect();
    }
}

