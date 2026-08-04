package org.wwz.ai.infrastructure.dataquery.jdbc.connection;





import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.wwz.ai.domain.agent.reactor.data.exception.JdbcBizException;
import org.wwz.ai.infrastructure.dataquery.jdbc.JdbcConnectionConfig;
import org.wwz.ai.infrastructure.dataquery.jdbc.catalog.JdbcCatalog;
import org.wwz.ai.infrastructure.dataquery.jdbc.dialect.JdbcDialect;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 单个 JDBC 数据源的运行时连接门面。
 *
 * <p>对象把连接池、方言、目录和原始连接配置聚合起来：语句创建委托给方言，连接获取
 * 负责有限重试。它不拥有连接关闭权，调用方在完成查询后必须关闭返回的 JDBC 连接。</p>
 */
@Slf4j
@Data
public class ConnectionWrapper {
    private JdbcDialect jdbcDialect;
    private JdbcCatalog catalog;
    private DatasourceWrapper datasourceWrapper;
    private JdbcConnectionConfig jdbcConnectionConfig;

    public PreparedStatement createPreparedStatement(Connection connection, String queryTemplate, Integer fetchSize) throws SQLException {
        // 由方言决定预编译语句的游标和 fetch 行为，避免连接层复制数据库差异。
        return jdbcDialect.createPreparedStatement(connection, queryTemplate, fetchSize);
    }

    public Statement createStatement(Connection connection, Integer fetchSize) throws SQLException {
        return jdbcDialect.createStatement(connection, fetchSize);
    }

    public Statement createStreamStatement(Connection connection, Integer fetchSize) throws SQLException {
        return jdbcDialect.createStreamStatement(connection, fetchSize);
    }

    public Connection getConnection() {
        // 连接获取失败只在配置的次数内重试；中断时立即转业务异常，不吞掉线程中断原因。
        int maxRetryTime = jdbcConnectionConfig.getMaxRetryTimes();
        int i = 0;
        Connection connection = null;
        while (i < maxRetryTime) {
            try {
                connection = datasourceWrapper.getDataSource().getConnection();
                log.info("获取数据库链接成功 poolId:{}", jdbcConnectionConfig.getKey());
                break;
            } catch (SQLException e) {
                if (i < maxRetryTime - 1) {
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException ie) {
                        throw new JdbcBizException(
                                "重试获取数据库链接失败",
                                ie);
                    }
                    log.warn("获取数据库链接失败, 重试次数 {}", i + 1);
                } else {
                    log.error("重试{}次后未后成功", i + 1);
                    throw new JdbcBizException(e);
                }
            }
            i++;
        }
        return connection;
    }
}
