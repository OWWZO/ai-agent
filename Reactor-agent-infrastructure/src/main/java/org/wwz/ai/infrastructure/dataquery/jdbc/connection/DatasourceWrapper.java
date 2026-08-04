package org.wwz.ai.infrastructure.dataquery.jdbc.connection;



import lombok.Data;
import org.wwz.ai.infrastructure.dataquery.jdbc.catalog.JdbcCatalog;
import org.wwz.ai.infrastructure.dataquery.jdbc.dialect.JdbcDialect;

import javax.sql.DataSource;

/**
 * 连接池运行时容器。
 *
 * <p>除数据源外同时保存匹配的方言、元数据目录和刷新时间，使连接池缓存命中后仍能
 * 复用同一套 SQL/元数据策略。它是基础设施内部对象，不作为 HTTP 或领域契约暴露。</p>
 */
@Data
public class DatasourceWrapper {

    private DataSource dataSource;

    private JdbcDialect jdbcDialect;

    private JdbcCatalog catalog;

    private Long freshTime;
}
