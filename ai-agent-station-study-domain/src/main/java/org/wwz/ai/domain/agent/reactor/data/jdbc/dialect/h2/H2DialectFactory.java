package org.wwz.ai.domain.agent.reactor.data.jdbc.dialect.h2;

import com.google.auto.service.AutoService;
import org.wwz.ai.domain.agent.reactor.data.jdbc.dialect.DialectEnum;
import org.wwz.ai.domain.agent.reactor.data.jdbc.dialect.JdbcDialect;
import org.wwz.ai.domain.agent.reactor.data.jdbc.dialect.JdbcDialectFactory;


@AutoService(JdbcDialectFactory.class)
public class H2DialectFactory implements JdbcDialectFactory {
    @Override
    public boolean acceptsURL(String url) {
        return url.startsWith(DialectEnum.H2.getUrlPrefix());
    }

    @Override
    public JdbcDialect create() {
        return new H2Dialect();
    }
}
