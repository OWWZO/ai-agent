package org.wwz.ai.infrastructure.dataquery.jdbc.catalog;


import org.wwz.ai.infrastructure.dataquery.jdbc.dialect.DialectEnum;

public interface JdbcCatalogFactory {

    DialectEnum jdbcDialect();

    /**
     * Creates a {@link JdbcCatalog} using the options.
     */
    JdbcCatalog createCatalog();
}

