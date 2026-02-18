package org.wwz.ai.domain.agent.genie.data.jdbc.dialect;

public interface JdbcDialectFactory {

    boolean acceptsURL(String url);

    JdbcDialect create();
}
