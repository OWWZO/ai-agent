package org.wwz.ai.test.domain.dataagent;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.rag.model.query.SqlExecutionResult;
import org.wwz.ai.infrastructure.dataquery.jdbc.DataQueryExecutionAdapter;
import org.wwz.ai.infrastructure.dataquery.jdbc.JdbcDataSourceProperties;
import org.wwz.ai.infrastructure.dataquery.provider.jdbc.JdbcDataProvider;
import org.wwz.ai.infrastructure.dataquery.provider.jdbc.JdbcQueryRequest;

import java.sql.SQLException;

public class DataQueryExecutionAdapterTest {

    @Test
    public void translatesJdbcFailureAtInfrastructureBoundary() {
        SQLException failure = new SQLException("query timeout");
        JdbcDataProvider provider = new JdbcDataProvider() {
            @Override
            public SqlExecutionResult queryData(JdbcQueryRequest request) throws SQLException {
                throw failure;
            }
        };
        JdbcDataSourceProperties properties = new JdbcDataSourceProperties();
        properties.setUrl("jdbc:mysql://localhost:3306/sales");
        properties.setType("mysql");
        DataQueryExecutionAdapter adapter = new DataQueryExecutionAdapter(provider, properties);

        IllegalStateException translated = Assert.assertThrows(IllegalStateException.class,
                () -> adapter.query("SELECT 1"));

        Assert.assertEquals("Data query execution failed", translated.getMessage());
        Assert.assertSame(failure, translated.getCause());
    }
}
