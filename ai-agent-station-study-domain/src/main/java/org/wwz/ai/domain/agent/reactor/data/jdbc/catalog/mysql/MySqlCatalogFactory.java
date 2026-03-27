package org.wwz.ai.domain.agent.reactor.data.jdbc.catalog.mysql;

import com.google.auto.service.AutoService;
import org.wwz.ai.domain.agent.reactor.data.jdbc.catalog.JdbcCatalog;
import org.wwz.ai.domain.agent.reactor.data.jdbc.catalog.JdbcCatalogFactory;
import org.wwz.ai.domain.agent.reactor.data.jdbc.dialect.DialectEnum;

@AutoService(JdbcCatalogFactory.class)
public class MySqlCatalogFactory implements JdbcCatalogFactory {
    @Override
    public DialectEnum jdbcDialect() {
        return DialectEnum.MYSQL;
    }

    @Override
    public JdbcCatalog createCatalog() {
        return new MySqlCatalog();
    }
}
