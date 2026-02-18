package org.wwz.ai.domain.agent.genie.data.jdbc.connection;



import lombok.Data;
import org.wwz.ai.domain.agent.genie.data.jdbc.catalog.JdbcCatalog;
import org.wwz.ai.domain.agent.genie.data.jdbc.dialect.JdbcDialect;

import javax.sql.DataSource;

@Data
public class DatasourceWrapper {

    private DataSource dataSource;

    private JdbcDialect jdbcDialect;

    private JdbcCatalog catalog;

    private Long freshTime;
}
