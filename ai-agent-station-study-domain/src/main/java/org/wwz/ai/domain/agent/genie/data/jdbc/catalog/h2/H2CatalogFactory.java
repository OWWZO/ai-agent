package org.wwz.ai.domain.agent.genie.data.jdbc.catalog.h2;

import com.google.auto.service.AutoService;
import org.wwz.ai.domain.agent.genie.data.jdbc.catalog.JdbcCatalog;
import org.wwz.ai.domain.agent.genie.data.jdbc.catalog.JdbcCatalogFactory;
import org.wwz.ai.domain.agent.genie.data.jdbc.dialect.DialectEnum;


@AutoService(JdbcCatalogFactory.class)
public class H2CatalogFactory implements JdbcCatalogFactory {
    @Override
    public DialectEnum jdbcDialect() {
        return DialectEnum.H2;
    }

    @Override
    public JdbcCatalog createCatalog() {
        return new H2SqlCatalog();
    }
}
