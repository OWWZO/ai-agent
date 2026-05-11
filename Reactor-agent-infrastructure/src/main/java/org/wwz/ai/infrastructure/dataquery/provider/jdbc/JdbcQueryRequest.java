package org.wwz.ai.infrastructure.dataquery.provider.jdbc;


import lombok.Data;
import org.wwz.ai.infrastructure.dataquery.jdbc.JdbcConnectionConfig;
import org.wwz.ai.domain.agent.reactor.data.provider.DataQueryRequest;

@Data
public class JdbcQueryRequest implements DataQueryRequest {

    private JdbcConnectionConfig jdbcConnectionConfig;
    private String sql;
    private int limit;

    private int pageIndex;
    private int pageSize;
}

