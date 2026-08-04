package org.wwz.ai.infrastructure.dataquery.jdbc.dialect;

/** 根据 JDBC URL 判断并创建数据库方言的 SPI 工厂。 */
public interface JdbcDialectFactory {

    /** 判断该工厂是否声明支持给定 JDBC URL。 */
    boolean acceptsURL(String url);

    /** 创建与 URL 匹配的方言实现。 */
    JdbcDialect create();
}
