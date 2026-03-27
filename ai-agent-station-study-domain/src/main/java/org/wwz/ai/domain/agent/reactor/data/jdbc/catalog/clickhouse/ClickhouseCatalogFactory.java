package org.wwz.ai.domain.agent.reactor.data.jdbc.catalog.clickhouse;

import com.google.auto.service.AutoService;
import org.wwz.ai.domain.agent.reactor.data.jdbc.catalog.JdbcCatalog;
import org.wwz.ai.domain.agent.reactor.data.jdbc.catalog.JdbcCatalogFactory;
import org.wwz.ai.domain.agent.reactor.data.jdbc.dialect.DialectEnum;


@AutoService(JdbcCatalogFactory.class)
public class ClickhouseCatalogFactory implements JdbcCatalogFactory {
    @Override
    public DialectEnum jdbcDialect() {
        return DialectEnum.CLICKHOUSE;
    }

    @Override
    public JdbcCatalog createCatalog() {
        return new ClickhouseCatalog();
    }
}
