package org.wwz.ai.infrastructure.dataquery.jdbc.dialect.clickhouse;




import org.wwz.ai.infrastructure.dataquery.jdbc.dialect.DialectEnum;
import org.wwz.ai.infrastructure.dataquery.jdbc.dialect.JdbcDialect;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** ClickHouse 方言，覆盖导出流的 fetch 行为以适配其 JDBC 驱动。 */
public class ClickhouseDialect implements JdbcDialect {
    @Override
    public DialectEnum dialectName() {
        return DialectEnum.CLICKHOUSE;
    }

    @Override
    public String driverName() {
        return "com.clickhouse.jdbc.ClickHouseDriver";
    }

    @Override
    public Statement createStreamStatement(Connection connection, Integer fetchSize) throws SQLException {
        // ClickHouse 不采用通用的 Integer.MIN_VALUE 流式约定，使用明确导出批量大小。
        Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
        statement.setFetchSize(DEFAULT_EXPORT_FETCH_SIZE);
        statement.setMaxRows(EXPORT_MAX_SIZE);
        return statement;
    }

}
