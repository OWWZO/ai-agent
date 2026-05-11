
package org.wwz.ai.infrastructure.dataquery.jdbc.catalog.h2;


import lombok.extern.slf4j.Slf4j;
import org.wwz.ai.domain.agent.reactor.data.SimpleTable;
import org.wwz.ai.domain.agent.reactor.data.TableColumn;
import org.wwz.ai.domain.agent.reactor.data.exception.CatalogException;
import org.wwz.ai.infrastructure.dataquery.jdbc.catalog.AbstractJdbcCatalog;
import org.wwz.ai.domain.agent.reactor.data.model.StandardColumnType;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
public class H2SqlCatalog extends AbstractJdbcCatalog {

    protected static final Set<String> SYS_DATABASES = new HashSet<>(4);
    private static final String SELECT_COLUMNS_SQL_TEMPLATE =
            "SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA IN  ('%s') AND TABLE_NAME ='%s' ORDER BY ORDINAL_POSITION ASC";

    static {
        SYS_DATABASES.add("information_schema");
        SYS_DATABASES.add("mysql");
        SYS_DATABASES.add("performance_schema");
        SYS_DATABASES.add("sys");
    }

    @Override
    public List<SimpleTable> listTables(Connection connection, String schema) throws CatalogException {
        String sql = "show tables ";
        try (PreparedStatement prepared = connection.prepareStatement(sql);
             ResultSet rs = prepared.executeQuery()) {
            List<SimpleTable> tables = new ArrayList<>();
            while (rs.next()) {
                SimpleTable st = new SimpleTable();
                st.setTableName(rs.getString(1));
                tables.add(st);
            }
            return tables;
        } catch (SQLException e) {
            throw new CatalogException("获取数据库表失败", e);
        }
    }

    public String typeConvertMysql(String type) {
        return switch (type) {
            case "DATE", "TIME", "TIMESTAMP" -> StandardColumnType.DATE.name();
            case "TINYINT", "SMALLINT", "INTEGER", "BIGINT", "FLOAT", "DOUBLE", "NUMERIC", "DECIMAL" ->
                    StandardColumnType.DECIMAL.name();
            default -> StandardColumnType.VARCHAR.name();
        };
    }

    @Override
    public List<TableColumn> getTableColumns(Connection connection, String tablePath, String schema) throws CatalogException {
        String sql = String.format(
                SELECT_COLUMNS_SQL_TEMPLATE, "PUBLIC", tablePath.toUpperCase());

        try (Statement prepared = connection.createStatement();
             ResultSet rs = prepared.executeQuery(sql)) {
            int i = 1;
            List<TableColumn> columnList = new ArrayList<>();
            while (rs.next()) {
                String columnName = rs.getString("column_name");
                String jdbcDataType = rs.getString("data_type").toUpperCase();
                String dataType = typeConvertMysql(jdbcDataType);
                String comment = rs.getString("remarks");
                TableColumn column = TableColumn.builder().name(columnName)
                        .comment(comment)
                        .dataType(dataType)
                        .originDataType(jdbcDataType)
                        .nullable(rs.getBoolean("is_nullable"))
                        .position(i++)
                        .build();
                columnList.add(column);
            }
            return columnList;

        } catch (Exception e) {
            throw new CatalogException(
                    String.format("获取表字段信息失败 %s", tablePath), e);
        }
    }
}

