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

/**
 * JDBC 查询结果适配器。
 *
 * <p>该类负责取得连接、按方言创建查询语句、读取 ResultSet 并物化为领域
 * {@link QueryResult}；SQL 生成和连接池生命周期分别由请求对象及连接门面负责。结果
 * 中同时保留最终 SQL、连接耗时、列信息和数据行，供问数上层展示和诊断。</p>
 */
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
        // 查询结果同时承载执行 SQL、连接耗时和数据行，供上层展示结果并定位慢查询。
        QueryResult queryResult = new QueryResult();
        long queryStartTime = System.currentTimeMillis();
        queryResult.setQueryStartTime(queryStartTime);
        final ConnectionWrapper wrapper = JdbcConnectionFactory.getConnection(request.getJdbcConnectionConfig());
        // 先由连接包装器按方言改写 SQL，再记录最终执行文本，日志和返回结果保持一致。
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
                // 结果按列标签建行，并按 JDBC 类型做最小转换，避免 BigDecimal/整数类型在后续 JSON 序列化时失真。
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
        // 连通性测试只申请并关闭连接，不执行用户 SQL，避免测试请求产生业务副作用。
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
