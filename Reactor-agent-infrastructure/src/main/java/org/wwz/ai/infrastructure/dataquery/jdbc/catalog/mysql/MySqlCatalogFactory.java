package org.wwz.ai.infrastructure.dataquery.jdbc.catalog.mysql;

import com.google.auto.service.AutoService;
import org.wwz.ai.infrastructure.dataquery.jdbc.catalog.JdbcCatalog;
import org.wwz.ai.infrastructure.dataquery.jdbc.catalog.JdbcCatalogFactory;
import org.wwz.ai.infrastructure.dataquery.jdbc.dialect.DialectEnum;

/** 通过 SPI 注册 MySQL 元数据目录。 */
@AutoService(JdbcCatalogFactory.class)
public class MySqlCatalogFactory implements JdbcCatalogFactory {
    @Override
    public DialectEnum jdbcDialect() {
        // 工厂标识必须与连接方言一致，供 JdbcCatalogLoader 做唯一匹配。
        return DialectEnum.MYSQL;
    }

    @Override
    public JdbcCatalog createCatalog() {
        // 目录本身无连接状态，每次按方言创建轻量实例即可。
        return new MySqlCatalog();
    }
}
