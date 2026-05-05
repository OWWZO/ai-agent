package org.wwz.ai.domain.agent.adapter.port;

import org.wwz.ai.domain.agent.reactor.config.data.DbConfig;
import org.wwz.ai.domain.agent.reactor.data.QueryResult;

import java.sql.SQLException;

/**
 * 数据查询执行端口。
 * domain 只描述“基于问数数据源执行 SQL”这件事，JDBC 连接与方言细节由 infrastructure 承接。
 */
public interface DataQueryExecutionPort {

    /**
     * 执行 SQL 查询并返回结构化结果。
     */
    QueryResult query(DbConfig dbConfig, String sql) throws SQLException;

    /**
     * 执行带 limit 的 SQL 查询。
     */
    QueryResult query(DbConfig dbConfig, String sql, int limit) throws SQLException;
}
