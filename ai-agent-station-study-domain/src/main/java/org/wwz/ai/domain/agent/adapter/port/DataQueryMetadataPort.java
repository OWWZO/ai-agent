package org.wwz.ai.domain.agent.adapter.port;

import org.wwz.ai.domain.agent.reactor.config.data.DbConfig;
import org.wwz.ai.domain.agent.reactor.data.TableColumn;

import java.sql.SQLException;
import java.util.List;

/**
 * 数据查询元信息端口。
 * 用于按数据源读取表字段定义或 SQL 结果字段定义，避免 domain 直接依赖 JDBC 元信息读取器。
 */
public interface DataQueryMetadataPort {

    /**
     * 读取表字段定义。
     */
    List<TableColumn> queryColumns(DbConfig dbConfig, String tableName, String schema) throws SQLException;

    /**
     * 读取 SQL 结果集字段定义。
     */
    List<TableColumn> getTableColumnsOfSql(DbConfig dbConfig, String sql, int limit) throws SQLException;
}
