package org.wwz.ai.domain.agent.reactor.data.provider;




import org.wwz.ai.domain.agent.reactor.data.SimpleTable;
import org.wwz.ai.domain.agent.reactor.data.TableColumn;

import java.sql.SQLException;
import java.util.List;

/**
 * 数据查询元数据端口。
 * <p>为问数和 SQL 生成提供表、列及类型信息，不直接承担实际数据查询。</p>
 */
public interface DataMetaProvider<T extends DataQueryRequest> {

    List<SimpleTable> queryTables(T request, String schema) throws Exception;

    List<TableColumn> queryColumns(T request, String tableName, String schema) throws Exception;

    List<TableColumn> getTableColumnsOfSql(T request) throws SQLException;
}
