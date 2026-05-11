package org.wwz.ai.infrastructure.dataquery.jdbc.dialect;

public interface JdbcDialectFactory {

    boolean acceptsURL(String url);

    JdbcDialect create();
}

