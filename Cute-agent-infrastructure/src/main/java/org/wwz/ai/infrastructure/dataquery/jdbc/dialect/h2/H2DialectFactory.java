package org.wwz.ai.infrastructure.dataquery.jdbc.dialect.h2;

import com.google.auto.service.AutoService;
import org.wwz.ai.infrastructure.dataquery.jdbc.dialect.DialectEnum;
import org.wwz.ai.infrastructure.dataquery.jdbc.dialect.JdbcDialect;
import org.wwz.ai.infrastructure.dataquery.jdbc.dialect.JdbcDialectFactory;


/** 通过 JDBC URL 前缀注册 H2 方言。 */
@AutoService(JdbcDialectFactory.class)
public class H2DialectFactory implements JdbcDialectFactory {
    @Override
    public boolean acceptsURL(String url) {
        // H2 内存/文件连接都共享 jdbc:h2 前缀，由 H2 驱动继续解析具体地址。
        return url.startsWith(DialectEnum.H2.getUrlPrefix());
    }

    @Override
    public JdbcDialect create() {
        return new H2Dialect();
    }
}
