package org.wwz.ai.infrastructure.dataquery.jdbc.dialect.mysql;




import org.wwz.ai.infrastructure.dataquery.jdbc.dialect.DialectEnum;
import org.wwz.ai.infrastructure.dataquery.jdbc.dialect.JdbcDialect;

import java.util.Properties;

public class MysqlDialect implements JdbcDialect {
    @Override
    public DialectEnum dialectName() {
        return DialectEnum.MYSQL;
    }

    @Override
    public String driverName() {
        // 使用 MySQL 8+ 官方驱动类，避免旧驱动弃用告警。
        return "com.mysql.cj.jdbc.Driver";
    }

    @Override
    public Properties defaultProperties() {
        Properties properties = new Properties();
        properties.setProperty("remarks", "true");
        properties.setProperty("useInformationSchema", "true");
        return properties;
    }
}

