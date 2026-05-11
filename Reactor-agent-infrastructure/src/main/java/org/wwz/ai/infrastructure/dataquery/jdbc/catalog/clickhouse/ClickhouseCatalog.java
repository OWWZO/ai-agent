package org.wwz.ai.infrastructure.dataquery.jdbc.catalog.clickhouse;


import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.reactor.data.SimpleTable;
import org.wwz.ai.domain.agent.reactor.data.exception.CatalogException;
import org.wwz.ai.infrastructure.dataquery.jdbc.catalog.AbstractJdbcCatalog;
import org.wwz.ai.domain.agent.reactor.data.model.StandardColumnType;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class ClickhouseCatalog extends AbstractJdbcCatalog {


    @Override
    public List<SimpleTable> listTables(Connection connection, String schema) throws CatalogException {
        String sql = "SELECT concat(database,'.',name) as name FROM system.tables WHERE database = '" + schema + "'";
        try (Statement prepared = connection.createStatement();
             ResultSet rs = prepared.executeQuery(sql)) {
            List<SimpleTable> tables = new ArrayList<>();
            while (rs.next()) {
                SimpleTable st = new SimpleTable();
                st.setTableSchema(schema);
                st.setTableName(rs.getString("name"));
                tables.add(st);
            }
            return tables;

        } catch (Exception e) {
            throw new CatalogException(
                    String.format("获取数据库表失败 %s ", schema), e);
        }
    }


    public String getColumnType(String columnType) {
        return switch (StandardColumnType.of(columnType)) {
            case DECIMAL -> "Decimal64(4)";
            case DATE -> "DateTime";
            default -> "String";
        };
    }


    public BigDecimal parseDecimal(String value, String fieldName) {
        BigDecimal decimal = null;
        if (StringUtils.isNotBlank(value)) {
            try {
                decimal = new BigDecimal(value);
            } catch (Exception e) {
                throw new CatalogException("字段" + fieldName + "值\"" + value + "\"转换成数值失败", e);
            }
        }
        return decimal;
    }
}

