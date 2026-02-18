package org.wwz.ai.domain.agent.genie.data.jdbc.catalog;


import org.wwz.ai.domain.agent.genie.data.jdbc.dialect.DialectEnum;

public interface JdbcCatalogFactory {

    DialectEnum jdbcDialect();

    /**
     * Creates a {@link JdbcCatalog} using the options.
     */
    JdbcCatalog createCatalog();
}
