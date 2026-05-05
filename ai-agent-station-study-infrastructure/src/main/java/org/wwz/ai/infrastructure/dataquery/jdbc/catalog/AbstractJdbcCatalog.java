package org.wwz.ai.infrastructure.dataquery.jdbc.catalog;


import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.reactor.data.SimpleTable;
import org.wwz.ai.domain.agent.reactor.data.TableColumn;
import org.wwz.ai.domain.agent.reactor.data.exception.CatalogException;
import org.wwz.ai.domain.agent.reactor.data.model.StandardColumnType;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractJdbcCatalog implements JdbcCatalog {

    @Override
    public List<SimpleTable> listTables(Connection connection, String schema) throws CatalogException {
        try (ResultSet rs = connection.getMetaData().getTables(null,
                schema, null, null)) {
            List<SimpleTable> tables = new ArrayList<>();
            while (rs.next()) {
                SimpleTable st = new SimpleTable();
                st.setTableName(rs.getString("TABLE_NAME"));
                st.setComments(rs.getString("REMARKS"));
                st.setTableType(rs.getString("TABLE_TYPE"));
                String tableSchem = rs.getString("TABLE_SCHEM");
                if (StringUtils.isBlank(tableSchem)) {
                    tableSchem = rs.getString("TABLE_CAT");
                }
                st.setTableSchema(tableSchem);
                tables.add(st);
            }
            return tables;
        } catch (SQLException e) {
            throw new CatalogException("Failed listing database in catalog %s", e);
        }
    }


    @Override
    public List<TableColumn>  getTableColumns(Connection connection, String tablePath, String schema) throws CatalogException {
        try (ResultSet rs = connection.getMetaData().getColumns(null,
                null, tablePath, null)) {

            List<TableColumn> columnList = new ArrayList<>();
            while (rs.next()) {
                String name = rs.getString("COLUMN_NAME");
                int intDataType = rs.getInt("DATA_TYPE");
                JDBCType jdbcType = JDBCType.valueOf(intDataType);
                String dataType = typeConvert(jdbcType);

                TableColumn column = TableColumn.builder().name(name)
                        .columnLength(rs.getInt("COLUMN_SIZE"))
                        .comment(rs.getString("REMARKS"))
                        .dataType(dataType)
                        .originDataType(jdbcType.name())
                        .position(rs.getInt("ORDINAL_POSITION"))
                        .defaultValue(rs.getObject("COLUMN_DEF"))
                        .nullable(DatabaseMetaData.columnNoNulls != rs.getInt("NULLABLE")).build();
                columnList.add(column);
            }
            return columnList;

        } catch (Exception e) {
            throw new CatalogException(
                    String.format("Failed getting table %s", tablePath), e);
        }
    }

    public static   String typeConvert(JDBCType jdbcType){
        return switch (jdbcType) {
            case DATE, TIME, TIMESTAMP -> StandardColumnType.DATE.name();
            case TINYINT, SMALLINT, INTEGER, BIGINT, FLOAT, DOUBLE, NUMERIC, DECIMAL ->
                    StandardColumnType.DECIMAL.name();
            default -> StandardColumnType.VARCHAR.name();
        };
    }

}

