package org.wwz.ai.infrastructure.dataquery.jdbc.connection;

import com.google.common.base.Preconditions;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.infrastructure.dataquery.jdbc.JdbcConnectionConfig;

import java.sql.SQLException;

@Slf4j
public class JdbcConnectionFactory {


    public static ConnectionWrapper getConnection(JdbcConnectionConfig config) throws SQLException {
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

