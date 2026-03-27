package org.wwz.ai.domain.agent.reactor.data.provider.jdbc;


import lombok.Data;
import org.wwz.ai.domain.agent.reactor.data.jdbc.JdbcConnectionConfig;
import org.wwz.ai.domain.agent.reactor.data.provider.DataQueryRequest;

@Data
public class JdbcQueryRequest implements DataQueryRequest {

    private JdbcConnectionConfig jdbcConnectionConfig;
    private String sql;
    private int limit;

    private int pageIndex;
    private int pageSize;
}
