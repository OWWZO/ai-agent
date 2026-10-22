package org.wwz.ai.test;

import org.junit.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.util.Assert;
import org.wwz.ai.config.DataSourceConfig;

/**
 * 数据源配置 profile 回归。
 * test profile 明确禁用数据库链路时，不应再强制解析 mysql 相关占位符。
 */
public class DataSourceConfigProfileTest {

    @Test
    public void shouldNotCreateDatasourceBeansUnderTestProfile() {
        try (ConfigurableApplicationContext ignored = new SpringApplicationBuilder(DataSourceProfileProbeApplication.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--spring.main.banner-mode=off",
                        "--spring.profiles.active=test"
                )) {
            // 只要上下文能启动成功，就说明 test profile 下不会再强制解析数据库占位符。
        }
    }

    @Test
    public void shouldNotCreateDatasourceBeansWhenMysqlPropertiesAreMissing() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(DataSourceProfileProbeApplication.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--spring.main.banner-mode=off",
                        "--spring.profiles.active=custom",
                        "--spring.config.location=classpath:/application-test.yml"
                )) {
            Assert.isTrue(!context.containsBean("mysqlDataSource"), "mysqlDataSource 不应在缺失 mysql 配置时创建");
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(DataSourceConfig.class)
    static class DataSourceProfileProbeApplication {
    }
}
