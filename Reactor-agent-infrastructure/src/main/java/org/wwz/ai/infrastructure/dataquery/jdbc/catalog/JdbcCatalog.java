
package org.wwz.ai.infrastructure.dataquery.jdbc.catalog;




import org.wwz.ai.domain.agent.reactor.data.SimpleTable;
import org.wwz.ai.domain.agent.reactor.data.TableColumn;
import org.wwz.ai.domain.agent.reactor.data.exception.CatalogException;

import java.sql.Connection;
import java.util.List;

/**
 * JDBC 元数据目录抽象。
 *
 * <p>目录只负责从已建立的连接读取表和列的结构信息，并转换为数据问数领域模型；
 * 不负责连接创建、连接池管理或 SQL 查询执行。不同数据库的元数据差异由具体目录
 * 实现隔离。</p>
 */
public interface JdbcCatalog {

    /** 查询指定 schema 下可供问数使用的表。 */
    List<SimpleTable> listTables(Connection connection, String schema) throws CatalogException;

    /** 查询指定表的列信息，并保留原始类型与统一类型。 */
    List<TableColumn> getTableColumns(Connection connection, String tablePath, String schema) throws CatalogException;
}
