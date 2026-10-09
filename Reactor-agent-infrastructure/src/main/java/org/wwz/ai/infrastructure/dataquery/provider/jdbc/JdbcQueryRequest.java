package org.wwz.ai.infrastructure.dataquery.provider.jdbc;


import lombok.Data;
import org.wwz.ai.infrastructure.dataquery.jdbc.JdbcConnectionConfig;

@Data
public class JdbcQueryRequest {

    private JdbcConnectionConfig jdbcConnectionConfig;
    private String sql;
    private int limit;

    private int pageIndex;
    private int pageSize;
}
