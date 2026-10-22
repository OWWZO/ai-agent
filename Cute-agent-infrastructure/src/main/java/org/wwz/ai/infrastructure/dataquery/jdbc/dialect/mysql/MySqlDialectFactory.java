package org.wwz.ai.infrastructure.dataquery.jdbc.dialect.mysql;


import com.google.auto.service.AutoService;
import org.wwz.ai.infrastructure.dataquery.jdbc.dialect.DialectEnum;
import org.wwz.ai.infrastructure.dataquery.jdbc.dialect.JdbcDialect;
import org.wwz.ai.infrastructure.dataquery.jdbc.dialect.JdbcDialectFactory;

/** 通过 JDBC URL 前缀注册 MySQL 方言。 */
@AutoService(JdbcDialectFactory.class)
public class MySqlDialectFactory implements JdbcDialectFactory {
    @Override
    public boolean acceptsURL(String url) {
        // 只按官方 MySQL URL 前缀匹配，避免把其他兼容协议误判为 MySQL。
        return url.startsWith(DialectEnum.MYSQL.getUrlPrefix());
    }

    @Override
    public JdbcDialect create() {
        return new MysqlDialect();
    }
}
