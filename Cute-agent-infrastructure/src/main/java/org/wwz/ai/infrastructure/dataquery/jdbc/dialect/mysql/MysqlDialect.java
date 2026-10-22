package org.wwz.ai.infrastructure.dataquery.jdbc.dialect.mysql;




import org.wwz.ai.infrastructure.dataquery.jdbc.dialect.DialectEnum;
import org.wwz.ai.infrastructure.dataquery.jdbc.dialect.JdbcDialect;

import java.util.Properties;

/** MySQL 方言，使用只读查询默认能力并开启 INFORMATION_SCHEMA 元数据读取。 */
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
        // remarks 和 Information Schema 配置保证列注释可被目录层读取。
        Properties properties = new Properties();
        properties.setProperty("remarks", "true");
        properties.setProperty("useInformationSchema", "true");
        return properties;
    }
}
