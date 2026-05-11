package org.wwz.ai.infrastructure.dataquery.jdbc;


import lombok.Data;
import org.wwz.ai.infrastructure.dataquery.jdbc.dialect.DialectEnum;
import org.wwz.ai.domain.agent.reactor.data.provider.QueryConfig;

import java.io.Serializable;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

@Data
public class JdbcConnectionConfig implements Serializable, QueryConfig {
    private static final long serialVersionUID = 3205225174160474938L;


    private DialectEnum jdbcDialect;

    /**
     * 连接唯一标识
     */
    private String key;

    private String url;

    private String driverName;

    private String userName;

    private String password;

    private boolean proxy = false;


    private static final long CONNECTION_TIMEOUT = TimeUnit.SECONDS.toMillis(10000L);
    private static final long VALIDATION_TIMEOUT = TimeUnit.SECONDS.toMillis(300000L);
    private static final long IDLE_TIMEOUT = TimeUnit.MINUTES.toMillis(10000L);
    private static final long MAX_LIFETIME = TimeUnit.MINUTES.toMillis(30000L);
    private static final long KEEP_ALIVE_TIME = TimeUnit.MINUTES.toMillis(50000L);

    private Boolean readOnly;

    private Long connectionTimeout = CONNECTION_TIMEOUT;
    private Long validationTimeout = VALIDATION_TIMEOUT;
    private Long idleTimeout = IDLE_TIMEOUT;
    private Long maxLifetime = MAX_LIFETIME;
    private Long keepAliveTime = KEEP_ALIVE_TIME;
    private Integer maxPoolSize = 50;
    private Integer minIdle = 3;

    /**
     * 最大重试次数1
     */
    private int maxRetryTimes = 2;

    private Properties extConfig;
    /**
     * 是否缓存连接池
     */
    private boolean cachePools = true;

    private String dataSourceType;
    private Long freshTimestamp;

}

