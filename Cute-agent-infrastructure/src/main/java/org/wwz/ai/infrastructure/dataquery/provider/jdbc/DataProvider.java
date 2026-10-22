package org.wwz.ai.infrastructure.dataquery.provider.jdbc;

import org.wwz.ai.infrastructure.dataquery.provider.jdbc.JdbcQueryRequest;
import org.wwz.ai.domain.agent.rag.model.query.SqlExecutionResult;

import java.sql.SQLException;

public interface DataProvider<T extends JdbcQueryRequest> {
    SqlExecutionResult queryData(T request) throws SQLException;

    boolean queryForTest(T request);
}
