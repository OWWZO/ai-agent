package org.wwz.ai.domain.agent.reactor.data.jdbc.dialect;

public interface JdbcDialectFactory {

    boolean acceptsURL(String url);

    JdbcDialect create();
}
