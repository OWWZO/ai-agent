package org.wwz.ai.test;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * 配置文件加载回归。
 * 只验证 Spring Boot 默认配置装配结果，不拉起业务 Bean，避免噪音干扰根因判断。
 */
public class ConfigProfileLoadingTest {

    @Test
    public void shouldLoadMysqlDatasourcePropertiesFromDefaultConfigurationChain() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(ConfigProbeApplication.class)
                .web(WebApplicationType.NONE)
                .run("--spring.main.banner-mode=off")) {
            Assert.assertEquals("dev", context.getEnvironment().getProperty("spring.profiles.active"));
            Assert.assertEquals("com.mysql.cj.jdbc.Driver",
                    context.getEnvironment().getProperty("spring.datasource.mysql.driver-class-name"));
            Assert.assertNotNull(context.getEnvironment().getProperty("spring.datasource.mysql.url"));
        }
    }

    @TestConfiguration
    @ComponentScan(
            basePackages = "org.wwz.ai",
            excludeFilters = {
                    @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
                    @ComponentScan.Filter(type = FilterType.REGEX, pattern = "org\\.wwz\\.ai\\..*")
            }
    )
    static class ConfigProbeApplication {
    }
}
