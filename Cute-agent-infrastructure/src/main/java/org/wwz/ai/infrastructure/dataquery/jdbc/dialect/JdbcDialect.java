package org.wwz.ai.infrastructure.dataquery.jdbc.dialect;



import org.wwz.ai.infrastructure.dataquery.provider.jdbc.JdbcQueryRequest;

import java.sql.*;
import java.util.Properties;

/**
 * JDBC 方言能力抽象。
 *
 * <p>方言负责驱动标识、连接校验 SQL、语句游标参数和分页/限流 SQL 的差异；查询
 * 上层只依赖这些能力，不直接判断 MySQL、H2 或 ClickHouse。默认实现提供通用查询
 * 上限和分页行为，特殊数据库可覆盖相关方法。</p>
 */
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
        // 查询语句使用只读、正向游标，并把 fetchSize 同时作为单次查询行数上限。
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
        // 导出流采用驱动约定的最小 fetch 值，并设置独立的导出最大行数。
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
        // 外部页码按 1 开始，方言片段统一使用 0 开始的偏移量。
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
        // 先去除注释再判断 LIMIT，避免用户 SQL 中的注释文本触发重复分页。
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
        // 已经带 LIMIT 的 SQL 原样保留，否则追加当前方言的分页片段。
        String newSql = formatSql(sql);
        if (hasLimit(newSql)) {
            return newSql;
        }
        return formatPagingSql(start, pageSize, newSql);
    }

}
