package org.wwz.ai.infrastructure.dataquery.jdbc.catalog.mysql;

import com.google.auto.service.AutoService;
import org.wwz.ai.infrastructure.dataquery.jdbc.catalog.JdbcCatalog;
import org.wwz.ai.infrastructure.dataquery.jdbc.catalog.JdbcCatalogFactory;
import org.wwz.ai.infrastructure.dataquery.jdbc.dialect.DialectEnum;

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

