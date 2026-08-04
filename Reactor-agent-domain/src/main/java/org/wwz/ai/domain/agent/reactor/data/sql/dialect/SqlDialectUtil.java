package org.wwz.ai.domain.agent.reactor.data.sql.dialect;

import org.apache.calcite.sql.SqlDialect;
import org.apache.commons.lang3.StringUtils;

/**
 * SQL 方言选择工具，按配置字符串返回 MySQL 或 ClickHouse Calcite 方言。
 */
public class SqlDialectUtil {

    public static SqlDialect fromDialectString(String dialectString) {
        if (StringUtils.equalsIgnoreCase("clickhouse", StringUtils.trimToEmpty(dialectString))) {
            return ClickHouseSqlDialect2.DEFAULT;
        }
        return MysqlCustomSqlDialect.DEFAULT;
    }
}
