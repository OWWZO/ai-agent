package org.wwz.ai.infrastructure.dataquery.jdbc.catalog.h2;

import com.google.auto.service.AutoService;
import org.wwz.ai.infrastructure.dataquery.jdbc.catalog.JdbcCatalog;
import org.wwz.ai.infrastructure.dataquery.jdbc.catalog.JdbcCatalogFactory;
import org.wwz.ai.infrastructure.dataquery.jdbc.dialect.DialectEnum;


/** 通过 SPI 注册 H2 元数据目录。 */
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
