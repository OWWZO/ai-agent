package org.wwz.ai.infrastructure.dataquery.provider.jdbc;

import org.wwz.ai.domain.agent.rag.model.schema.DataQueryTable;
import org.wwz.ai.domain.agent.rag.model.schema.DataQueryTableColumn;

import java.sql.SQLException;
import java.util.List;

public interface DataMetaProvider<T extends JdbcQueryRequest> {
    List<DataQueryTable> queryTables(T request, String schemaPattern) throws SQLException;

    List<DataQueryTableColumn> queryColumns(T request, String tableName, String schema) throws SQLException;

    List<DataQueryTableColumn> getTableColumnsOfSql(T request) throws SQLException;
}
