
package org.wwz.ai.domain.agent.genie.data.jdbc.catalog;




import org.wwz.ai.domain.agent.genie.data.SimpleTable;
import org.wwz.ai.domain.agent.genie.data.TableColumn;
import org.wwz.ai.domain.agent.genie.data.exception.CatalogException;

import java.sql.Connection;
import java.util.List;

public interface JdbcCatalog {

    List<SimpleTable> listTables(Connection connection, String schema) throws CatalogException;

    List<TableColumn> getTableColumns(Connection connection, String tablePath, String schema) throws CatalogException;
}
