package org.wwz.ai.infrastructure.dataquery.provider.jdbc;


import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.reactor.data.QueryResult;
import org.wwz.ai.domain.agent.reactor.data.exception.JdbcBizException;
import org.wwz.ai.infrastructure.dataquery.jdbc.connection.ConnectionWrapper;
import org.wwz.ai.infrastructure.dataquery.jdbc.connection.JdbcConnectionFactory;
import org.wwz.ai.domain.agent.reactor.data.provider.DataProvider;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.sql.Types.*;

@Service
@Slf4j
public class JdbcDataProvider implements DataProvider<JdbcQueryRequest> {

    protected List<String> getColumnList(ResultSetMetaData metaData) throws SQLException {
        List<String> columnList = new ArrayList<>();
        int columnCount = metaData.getColumnCount();
        for (int i = 1; i <= columnCount; i++) {
            columnList.add(metaData.getColumnLabel(i));
        }
        return columnList;
    }

    private Object parseValue(ResultSet rs, int columnType, int index) throws SQLException {
        //处理非标准数值类型导致jsf序列化失败问题
        switch (columnType) {
            case NUMERIC:
                return rs.getBigDecimal(index);
            case INTEGER:
                return rs.getObject(index, Integer.class);
            case SMALLINT:
                try {
                    return rs.getObject(index, Short.class);
                } catch (Exception ex) {
                    //doris查询数仓执行YEAR结果时会报这个错误：java.sql.SQLException: Conversion not supported for type java.lang.Short
                    return rs.getObject(index, Integer.class);
                }
            case BIGINT:
                return rs.getObject(index, Long.class);
            default:
                return rs.getObject(index);
        }
    }

    @Override
    public QueryResult queryData(JdbcQueryRequest request) throws SQLException {
        QueryResult queryResult = new QueryResult();
        long queryStartTime = System.currentTimeMillis();
        queryResult.setQueryStartTime(queryStartTime);
        final ConnectionWrapper wrapper = JdbcConnectionFactory.getConnection(request.getJdbcConnectionConfig());
        request.setSql(wrapper.getJdbcDialect().formatSql(request.getSql()));
        queryResult.setQuerySql(request.getSql());
        log.info("jdbc执行sql:{}", request.getSql());
        try (Connection connection = wrapper.getConnection()) {
            long getConnectionTime = System.currentTimeMillis();
            queryResult.setCreateConnectionTime(getConnectionTime - queryStartTime);
            try (
                    Statement ps = wrapper.createStatement(connection, request.getLimit());
                    ResultSet rs = ps.executeQuery(request.getSql())) {
                List<Map<String, Object>> result = new ArrayList<>();
                ResultSetMetaData metaData = rs.getMetaData();
                List<String> columnList = getColumnList(metaData);
                queryResult.setColumnList(columnList);
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    for (int i = 1; i <= columnList.size(); i++) {
                        int columnType = metaData.getColumnType(i);
                        row.put(columnList.get(i - 1), parseValue(rs, columnType, i));
                    }
                    result.add(row);
                }
                queryResult.setDataList(result);
                queryResult.setDataSize((long) result.size());
                queryResult.setQuerySql(request.getSql());
                queryResult.setQueryEndTime(System.currentTimeMillis());
                return queryResult;
            }

        }
    }

    @Override
    public boolean queryForTest(JdbcQueryRequest request) {
        boolean success = false;
        request.getJdbcConnectionConfig().setMaxRetryTimes(1);
        try (Connection connection = JdbcConnectionFactory.getConnection(request.getJdbcConnectionConfig()).getConnection()) {
            success = true;
        } catch (Exception e) {
            log.warn("An error occurred while querying for test: {}", e.getMessage(), e);
            throw new JdbcBizException("数据库联通测试失败:" + e.getMessage());
        }
        return success;
    }

}

