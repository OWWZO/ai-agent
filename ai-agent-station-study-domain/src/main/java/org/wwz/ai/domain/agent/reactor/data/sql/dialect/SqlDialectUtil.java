package org.wwz.ai.domain.agent.reactor.data.sql.dialect;

import org.apache.calcite.sql.SqlDialect;
import org.apache.commons.lang3.StringUtils;

public class SqlDialectUtil {

    public static SqlDialect fromDialectString(String dialectString) {
        if (StringUtils.equalsIgnoreCase("clickhouse", StringUtils.trimToEmpty(dialectString))) {
            return ClickHouseSqlDialect2.DEFAULT;
        }
        return MysqlCustomSqlDialect.DEFAULT;
    }
}

