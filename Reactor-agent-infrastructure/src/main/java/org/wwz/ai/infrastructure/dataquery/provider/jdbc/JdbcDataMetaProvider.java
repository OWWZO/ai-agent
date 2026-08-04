package org.wwz.ai.infrastructure.dataquery.provider.jdbc;


import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.reactor.data.SimpleTable;
import org.wwz.ai.domain.agent.reactor.data.TableColumn;
import org.wwz.ai.infrastructure.dataquery.jdbc.connection.ConnectionWrapper;
import org.wwz.ai.infrastructure.dataquery.jdbc.connection.JdbcConnectionFactory;
import org.wwz.ai.domain.agent.reactor.data.provider.DataMetaProvider;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import static org.wwz.ai.infrastructure.dataquery.jdbc.catalog.AbstractJdbcCatalog.typeConvert;


/**
 * JDBC 元数据查询适配器。
 *
 * <p>表/列目录查询委托给与数据源匹配的 {@code JdbcCatalog}；SQL 字段探测则只读取
 * ResultSetMetaData，不消费查询结果。该边界让 NL2SQL 可以预览结构，同时避免把元数据
 * 探测误当成一次完整数据查询。</p>
 */
@Slf4j
@Service
public class JdbcDataMetaProvider implements DataMetaProvider<JdbcQueryRequest> {

    @Override
    public List<SimpleTable> queryTables(JdbcQueryRequest request, String schemaPattern) throws SQLException {
        final ConnectionWrapper wrapper = JdbcConnectionFactory.getConnection(request.getJdbcConnectionConfig());
        try (Connection connection = wrapper.getConnection()) {
            return wrapper.getCatalog().listTables(connection, schemaPattern);
        }
    }

    @Override
    public List<TableColumn> queryColumns(JdbcQueryRequest request, String tableName, String schema) throws SQLException {
        final ConnectionWrapper wrapper = JdbcConnectionFactory.getConnection(request.getJdbcConnectionConfig());
        try (Connection connection = wrapper.getConnection()) {
            return wrapper.getCatalog().getTableColumns(connection, tableName, schema);
        }
    }

    @Override
    public List<TableColumn> getTableColumnsOfSql(JdbcQueryRequest request) throws SQLException {
        final ConnectionWrapper wrapper = JdbcConnectionFactory.getConnection(request.getJdbcConnectionConfig());
        // 元数据查询同样使用方言格式化和 limit，保证字段探测与真正执行 SQL 的约束一致。
        request.setSql(wrapper.getJdbcDialect().formatSql(request.getSql()));
        log.info("jdbc meta 执行sql:{}", request.getSql());
        List<TableColumn> columnList = new ArrayList<>();
        try (Connection connection = wrapper.getConnection()) {
            try (
                    Statement ps = wrapper.createStatement(connection, request.getLimit());
                    ResultSet rs = ps.executeQuery(request.getSql())) {
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                // 只读取 ResultSetMetaData，不消费数据行；因此该路径适合预览查询结构而不是返回数据。
                for (int i = 1; i <= columnCount; i++) {
                    String name = metaData.getColumnLabel(i);
                    int intDataType = metaData.getColumnType(i);
                    JDBCType jdbcType = JDBCType.valueOf(intDataType);
                    String dataType = typeConvert(jdbcType);
                    TableColumn column = TableColumn.builder().name(name)
                            .comment(name)
                            .dataType(dataType)
                            .originDataType(jdbcType.name())
                            .position(i + 1)
                            .build();
                    columnList.add(column);
                }
            }

        }
        return columnList;
    }

}
