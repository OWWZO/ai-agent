package org.wwz.ai.infrastructure.dataquery;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.adapter.port.DataQueryMetadataPort;
import org.wwz.ai.domain.agent.reactor.config.data.DbConfig;
import org.wwz.ai.domain.agent.reactor.data.TableColumn;
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

    @Override
    public List<TableColumn> queryColumns(DbConfig dbConfig, String tableName, String schema) throws SQLException {
        JdbcQueryRequest jdbcQueryRequest = new JdbcQueryRequest();
        jdbcQueryRequest.setJdbcConnectionConfig(JdbcUtils.parseJdbcConnectionConfig(dbConfig));
        return jdbcDataMetaProvider.queryColumns(jdbcQueryRequest, tableName, schema);
    }

    @Override
    public List<TableColumn> getTableColumnsOfSql(DbConfig dbConfig, String sql, int limit) throws SQLException {
        JdbcQueryRequest jdbcQueryRequest = new JdbcQueryRequest();
        jdbcQueryRequest.setJdbcConnectionConfig(JdbcUtils.parseJdbcConnectionConfig(dbConfig));
        jdbcQueryRequest.setSql(sql);
        jdbcQueryRequest.setLimit(limit);
        return jdbcDataMetaProvider.getTableColumnsOfSql(jdbcQueryRequest);
    }
}
