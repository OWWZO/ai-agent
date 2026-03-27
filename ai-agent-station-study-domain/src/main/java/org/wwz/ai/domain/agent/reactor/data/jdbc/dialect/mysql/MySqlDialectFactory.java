package org.wwz.ai.domain.agent.reactor.data.jdbc.dialect.mysql;


import com.google.auto.service.AutoService;
import org.wwz.ai.domain.agent.reactor.data.jdbc.dialect.DialectEnum;
import org.wwz.ai.domain.agent.reactor.data.jdbc.dialect.JdbcDialect;
import org.wwz.ai.domain.agent.reactor.data.jdbc.dialect.JdbcDialectFactory;

@AutoService(JdbcDialectFactory.class)
public class MySqlDialectFactory implements JdbcDialectFactory {
    @Override
    public boolean acceptsURL(String url) {
        return url.startsWith(DialectEnum.MYSQL.getUrlPrefix());
    }

    @Override
    public JdbcDialect create() {
        return new MysqlDialect();
    }
}
