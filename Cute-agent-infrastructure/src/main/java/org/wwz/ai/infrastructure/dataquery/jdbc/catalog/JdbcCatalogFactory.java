package org.wwz.ai.infrastructure.dataquery.jdbc.catalog;


import org.wwz.ai.infrastructure.dataquery.jdbc.dialect.DialectEnum;

/** 为一种数据库方言创建对应元数据目录的 SPI 工厂。 */
public interface JdbcCatalogFactory {

    /** 返回该工厂支持的方言。 */
    DialectEnum jdbcDialect();

    /**
     * 创建无状态的元数据目录实例；连接由调用方提供。
     */
    JdbcCatalog createCatalog();
}
