package org.wwz.ai.domain.agent.reactor.data.sql.dialect;

import org.apache.calcite.sql.SqlDialect;
import org.wwz.ai.domain.agent.reactor.data.jdbc.dialect.DialectEnum;

public class SqlDialectUtil {

    public static SqlDialect fromDialectString(String dialectString) {
        DialectEnum dialectEnum = DialectEnum.of(dialectString);
        return switch (dialectEnum) {
            case H2,MYSQL -> MysqlCustomSqlDialect.DEFAULT;
            case CLICKHOUSE -> ClickHouseSqlDialect2.DEFAULT;
        };
    }
}
