package org.wwz.ai.infrastructure.dataquery.jdbc;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.rag.model.schema.DataQueryTableColumn;
import org.wwz.ai.domain.agent.rag.port.DataQueryMetadataPort;
import org.wwz.ai.infrastructure.dataquery.jdbc.JdbcDataSourceProperties;
import org.wwz.ai.infrastructure.dataquery.provider.jdbc.JdbcDataMetaProvider;
import org.wwz.ai.infrastructure.dataquery.provider.jdbc.JdbcQueryRequest;
import org.wwz.ai.infrastructure.dataquery.util.JdbcUtils;

import java.sql.SQLException;
import java.util.List;

/**
 * JDBC 元信息端口实现。
 * 负责承接表结构和 SQL 结果字段的读取。
 */
@Component
@RequiredArgsConstructor
public class DataQueryMetadataAdapter implements DataQueryMetadataPort {

    private final JdbcDataMetaProvider jdbcDataMetaProvider;
    private final JdbcDataSourceProperties properties;

    @Override
    public List<DataQueryTableColumn> queryColumns(String tableName, String schema) {
        // 表结构读取复用同一 JDBC 配置解析，但由 catalog 负责数据库方言差异，适配器不直接访问 JDBC 元数据。
        JdbcQueryRequest jdbcQueryRequest = new JdbcQueryRequest();
        jdbcQueryRequest.setJdbcConnectionConfig(JdbcUtils.parseJdbcConnectionConfig(properties));
        try {
            return jdbcDataMetaProvider.queryColumns(jdbcQueryRequest, tableName, schema);
        } catch (SQLException exception) {
            throw new IllegalStateException("Data query metadata lookup failed", exception);
        }
    }

    @Override
    public List<DataQueryTableColumn> getTableColumnsOfSql(String sql, int limit) {
        // SQL 字段元数据走独立 provider，避免为了拿列定义把实际数据行读入 domain 查询结果。
        JdbcQueryRequest jdbcQueryRequest = new JdbcQueryRequest();
        jdbcQueryRequest.setJdbcConnectionConfig(JdbcUtils.parseJdbcConnectionConfig(properties));
        jdbcQueryRequest.setSql(sql);
        jdbcQueryRequest.setLimit(limit);
        try {
            return jdbcDataMetaProvider.getTableColumnsOfSql(jdbcQueryRequest);
        } catch (SQLException exception) {
            throw new IllegalStateException("SQL result metadata lookup failed", exception);
        }
    }
}
