package org.wwz.ai.domain.agent.reactor.data.jdbc.catalog;


import org.wwz.ai.domain.agent.reactor.data.jdbc.dialect.DialectEnum;

public interface JdbcCatalogFactory {

    DialectEnum jdbcDialect();

    /**
     * Creates a {@link JdbcCatalog} using the options.
     */
    JdbcCatalog createCatalog();
}
