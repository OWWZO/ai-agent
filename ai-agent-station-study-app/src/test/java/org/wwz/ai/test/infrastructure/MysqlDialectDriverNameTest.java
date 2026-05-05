package org.wwz.ai.test.infrastructure;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.infrastructure.dataquery.jdbc.dialect.mysql.MysqlDialect;

/**
 * MySQL JDBC 驱动类名回归测试。
 * 防止数据查询链路回退到已废弃的旧驱动类。
 */
public class MysqlDialectDriverNameTest {

    @Test
    public void shouldUseMysqlConnectorJDriverClassName() {
        Assert.assertEquals("com.mysql.cj.jdbc.Driver", new MysqlDialect().driverName());
    }
}
