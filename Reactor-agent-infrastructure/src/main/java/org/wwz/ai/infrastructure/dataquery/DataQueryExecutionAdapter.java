package org.wwz.ai.infrastructure.dataquery;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.adapter.port.DataQueryExecutionPort;
import org.wwz.ai.domain.agent.reactor.config.data.DbConfig;
import org.wwz.ai.domain.agent.reactor.data.QueryResult;
import org.wwz.ai.infrastructure.dataquery.provider.jdbc.JdbcDataProvider;
import org.wwz.ai.infrastructure.dataquery.provider.jdbc.JdbcQueryRequest;
import org.wwz.ai.infrastructure.dataquery.util.JdbcUtils;

import java.sql.SQLException;

/**
 * JDBC 数据查询端口实现。
 * 负责把 domain 语义请求翻译为 infrastructure 侧 JDBC 查询请求。
 */
@Component
@RequiredArgsConstructor
public class DataQueryExecutionAdapter implements DataQueryExecutionPort {

    private final JdbcDataProvider jdbcDataProvider;

    @Override
    public QueryResult query(DbConfig dbConfig, String sql) throws SQLException {
        return query(dbConfig, sql, 0);
    }

    @Override
    public QueryResult query(DbConfig dbConfig, String sql, int limit) throws SQLException {
        // 端口层保持 domain 请求简单；JDBC 方言、连接池和 Statement limit 全部在 infrastructure 请求中补齐。
        JdbcQueryRequest jdbcQueryRequest = new JdbcQueryRequest();
        jdbcQueryRequest.setJdbcConnectionConfig(JdbcUtils.parseJdbcConnectionConfig(dbConfig));
        jdbcQueryRequest.setSql(sql);
        jdbcQueryRequest.setLimit(limit);
        return jdbcDataProvider.queryData(jdbcQueryRequest);
    }
}
