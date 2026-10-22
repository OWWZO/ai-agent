package org.wwz.ai.infrastructure.dataquery.jdbc.dialect.h2;





import org.wwz.ai.infrastructure.dataquery.jdbc.dialect.DialectEnum;
import org.wwz.ai.infrastructure.dataquery.jdbc.dialect.JdbcDialect;

import java.util.Properties;

/** H2 方言，主要用于本地/测试环境并兼容 MySQL 模式的元数据属性。 */
public class H2Dialect implements JdbcDialect {
    @Override
    public DialectEnum dialectName() {
        return DialectEnum.H2;
    }

    @Override
    public String driverName() {
        return "org.h2.Driver";
    }

    @Override
    public Properties defaultProperties() {
        // 打开备注和 Information Schema 支持，使测试库返回与生产库相近的列元数据。
        Properties properties = new Properties();
        properties.setProperty("remarks", "true");
        properties.setProperty("useInformationSchema", "true");
        return properties;
    }
}
