package org.wwz.ai.infrastructure.dataquery.jdbc.catalog;


import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.reactor.data.SimpleTable;
import org.wwz.ai.domain.agent.reactor.data.TableColumn;
import org.wwz.ai.domain.agent.reactor.data.exception.CatalogException;
import org.wwz.ai.domain.agent.reactor.data.model.StandardColumnType;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 JDBC {@link DatabaseMetaData} 的通用目录实现。
 *
 * <p>通用实现负责把 JDBC 元数据映射为领域表/列模型，并将 JDBC 类型归一为问数
 * 使用的日期、数值和字符串三类；数据库对元数据 SQL 有特殊要求时，由具体方言目录
 * 覆盖方法。</p>
 */
public abstract class AbstractJdbcCatalog implements JdbcCatalog {

    @Override
    public List<SimpleTable> listTables(Connection connection, String schema) throws CatalogException {
        // ResultSet 由当前方法创建和关闭，调用方只持有连接，不承担元数据游标生命周期。
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
        // 列元数据同时保留原始 JDBC 类型和统一类型，前者用于展示，后者用于问数推理。
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
        // 统一类型刻意收敛为少量语义类别，避免模型直接处理各数据库的大量方言类型名。
        return switch (jdbcType) {
            case DATE, TIME, TIMESTAMP -> StandardColumnType.DATE.name();
            case TINYINT, SMALLINT, INTEGER, BIGINT, FLOAT, DOUBLE, NUMERIC, DECIMAL ->
                    StandardColumnType.DECIMAL.name();
            default -> StandardColumnType.VARCHAR.name();
        };
    }

}
