package org.wwz.ai.infrastructure.dataquery.jdbc.dialect.clickhouse;




import org.wwz.ai.infrastructure.dataquery.jdbc.dialect.DialectEnum;
import org.wwz.ai.infrastructure.dataquery.jdbc.dialect.JdbcDialect;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

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
        Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
        statement.setFetchSize(DEFAULT_EXPORT_FETCH_SIZE);
        statement.setMaxRows(EXPORT_MAX_SIZE);
        return statement;
    }

}

