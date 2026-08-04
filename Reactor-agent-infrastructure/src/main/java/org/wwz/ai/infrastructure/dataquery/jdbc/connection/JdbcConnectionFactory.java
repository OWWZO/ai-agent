package org.wwz.ai.infrastructure.dataquery.jdbc.connection;

import com.google.common.base.Preconditions;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.infrastructure.dataquery.jdbc.JdbcConnectionConfig;

import java.sql.SQLException;

/** 根据连接配置取得带方言能力的 JDBC 连接门面。 */
@Slf4j
public class JdbcConnectionFactory {


    public static ConnectionWrapper getConnection(JdbcConnectionConfig config) throws SQLException {
        // 先校验缓存键和 URL，再复用连接池；这样无效配置不会污染全局连接池缓存。
        Preconditions.checkArgument(StringUtils.isNoneBlank(config.getKey()), "The key of jdbc config is null");
        Preconditions.checkArgument(StringUtils.isNoneBlank(config.getUrl()), "The url of jdbc config is null");

        DatasourceWrapper datasourceWrapper = JdbcConnectionPools.getInstance()
                .getOrCreateConnectionPool(config);

        ConnectionWrapper connectionWrapper = new ConnectionWrapper();
        connectionWrapper.setJdbcDialect(datasourceWrapper.getJdbcDialect());
        connectionWrapper.setCatalog(datasourceWrapper.getCatalog());
        connectionWrapper.setDatasourceWrapper(datasourceWrapper);
        connectionWrapper.setJdbcConnectionConfig(config);

        return connectionWrapper;
    }

}
