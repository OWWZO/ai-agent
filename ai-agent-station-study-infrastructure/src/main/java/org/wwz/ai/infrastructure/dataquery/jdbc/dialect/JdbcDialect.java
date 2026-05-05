package org.wwz.ai.infrastructure.dataquery.jdbc.dialect;



import org.wwz.ai.infrastructure.dataquery.provider.jdbc.JdbcQueryRequest;

import java.sql.*;
import java.util.Properties;

public interface JdbcDialect {

    public static final int DEFAULT_FETCH_SIZE = 10000;
    public static final int DEFAULT_EXPORT_FETCH_SIZE = 1000;
    public static final int EXPORT_MAX_SIZE = 1000000;
    public static final int QUERY_MAX_TIME = 300;

    DialectEnum dialectName();

    String driverName();

    default String testSql() {
        return "SELECT 1";
    }

    default PreparedStatement createPreparedStatement(Connection connection, String queryTemplate, Integer fetchSize) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(queryTemplate, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
        if (fetchSize == null || fetchSize > DEFAULT_FETCH_SIZE || fetchSize <= 0) {
            fetchSize = DEFAULT_FETCH_SIZE;
        }
        statement.setFetchSize(fetchSize);
        statement.setMaxRows(fetchSize);
        statement.setQueryTimeout(QUERY_MAX_TIME);
        return statement;
    }

    default Statement createStatement(Connection connection, Integer fetchSize) throws SQLException {
        Statement statement = connection.createStatement();
        if (fetchSize == null || fetchSize > DEFAULT_FETCH_SIZE || fetchSize <= 0) {
            fetchSize = DEFAULT_FETCH_SIZE;
        }
        statement.setFetchSize(fetchSize);
        statement.setMaxRows(fetchSize);
        statement.setQueryTimeout(QUERY_MAX_TIME);
        return statement;
    }

    default Statement createStreamStatement(Connection connection, Integer fetchSize) throws SQLException {
        Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
        statement.setFetchSize(Integer.MIN_VALUE);
        statement.setMaxRows(EXPORT_MAX_SIZE);
        statement.setQueryTimeout(QUERY_MAX_TIME);
        return statement;
    }


    default String pagingHead(int start, int pageSize) {
        return "";
    }

    default String pagingEnd(int start, int pageSize) {
        return " LIMIT " + (start * pageSize) + "," + pageSize;
    }

    default String formatPagingSql(int start, int pageSize, String sql) {
        if (start < 0) {
            start = 1;
        }
        return pagingHead(start - 1, pageSize) + sql + pagingEnd(start - 1, pageSize);
    }

    default Properties defaultProperties() {
        return new Properties();
    }

    default String formatSql(String sql) {
        return sql;
    }

    default String setLimit(JdbcQueryRequest request) {
        return request.getSql();
    }

    default boolean hasLimit(String sql) {
        // 移除注释
        String newSql = sql.toUpperCase()
                .replaceAll("/\\*.*?\\*/", "")
                .replaceAll("--[^\\r\\n]*", " ")
                .replaceAll("#[^\\r\\n]*", " ")
                .replaceAll("\\s+", " ")
                .trim();
        //LIMIT n 或 LIMIT m,n 或 LIMIT n OFFSET m
        return newSql.matches("(?s).*\\bLIMIT\\s+\\d+\\s*(,\\s*\\d+\\s*)?(\\s|;|$).*") ||
                newSql.matches("(?s).*\\bLIMIT\\s+\\d+\\s+OFFSET\\s+\\d+\\s*(\\s|;|$).*");
    }

    default String formatLimitSql(int start, int pageSize, String sql) {
        String newSql = formatSql(sql);
        if (hasLimit(newSql)) {
            return newSql;
        }
        return formatPagingSql(start, pageSize, newSql);
    }

}

