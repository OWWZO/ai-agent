package org.wwz.ai.config;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.util.Objects;

/**
 * 根据环境参数创建业务主库数据源。
 */
@Configuration
@Conditional(DataSourceConfig.DatabaseProfileCondition.class)
public class DataSourceConfig {

    @Bean("mysqlDataSource")
    @Primary
    public DataSource mysqlDataSource(@Value("${spring.datasource.mysql.driver-class-name}") String driverClassName,
                                      @Value("${spring.datasource.mysql.url}") String url,
                                      @Value("${spring.datasource.mysql.username}") String username,
                                      @Value("${spring.datasource.mysql.password}") String password,
                                      @Value("${spring.datasource.mysql.hikari.maximum-pool-size:10}") int maximumPoolSize,
                                      @Value("${spring.datasource.mysql.hikari.minimum-idle:5}") int minimumIdle,
                                      @Value("${spring.datasource.mysql.hikari.idle-timeout:30000}") long idleTimeout,
                                      @Value("${spring.datasource.mysql.hikari.connection-timeout:30000}") long connectionTimeout,
                                      @Value("${spring.datasource.mysql.hikari.max-lifetime:1800000}") long maxLifetime) {
        // 连接池配置
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setDriverClassName(driverClassName);
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);

        dataSource.setMaximumPoolSize(maximumPoolSize);
        dataSource.setMinimumIdle(minimumIdle);
        dataSource.setIdleTimeout(idleTimeout);
        dataSource.setConnectionTimeout(connectionTimeout);
        dataSource.setMaxLifetime(maxLifetime);
        dataSource.setPoolName("MainHikariPool");

        return dataSource;
    }

    @Bean("sqlSessionFactory")
    public SqlSessionFactory sqlSessionFactory(@Qualifier("mysqlDataSource") DataSource mysqlDataSource) throws Exception {
        // 使用 MyBatis-Plus 的 SqlSessionFactory，确保 BaseMapper 通用方法能正确注入
        MybatisSqlSessionFactoryBean sqlSessionFactoryBean = new MybatisSqlSessionFactoryBean();
        sqlSessionFactoryBean.setDataSource(mysqlDataSource);
        sqlSessionFactoryBean.setGlobalConfig(buildMybatisPlusGlobalConfig());

        // 设置MyBatis配置文件位置
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        sqlSessionFactoryBean.setConfigLocation(resolver.getResource("classpath:/mybatis/config/mybatis-config.xml"));

        // 设置Mapper XML文件位置
        sqlSessionFactoryBean.setMapperLocations(resolver.getResources("classpath:/mybatis/mapper/*.xml"));

        return Objects.requireNonNull(sqlSessionFactoryBean.getObject());
    }

    /**
     * 显式指定 MyBatis-Plus 全局配置，避免逻辑删除默认值与库表约定不一致。
     */
    private GlobalConfig buildMybatisPlusGlobalConfig() {
        GlobalConfig globalConfig = new GlobalConfig();
        GlobalConfig.DbConfig dbConfig = new GlobalConfig.DbConfig();
        dbConfig.setIdType(IdType.AUTO);
        dbConfig.setLogicDeleteField("yn");
        dbConfig.setLogicDeleteValue("0");
        dbConfig.setLogicNotDeleteValue("1");
        globalConfig.setDbConfig(dbConfig);
        return globalConfig;
    }

    @Bean("sqlSessionTemplate")
    public SqlSessionTemplate sqlSessionTemplate(@Qualifier("sqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    /**
     * 仅在显式配置 mysql 数据源时启用数据库装配。
     * 这样 test profile 与无库探针场景都不会因为缺失占位符而提前失败。
     */
    static class DatabaseProfileCondition implements org.springframework.context.annotation.Condition {

        @Override
        public boolean matches(org.springframework.context.annotation.ConditionContext context, AnnotatedTypeMetadata metadata) {
            Environment environment = context.getEnvironment();
            for (String profile : environment.getActiveProfiles()) {
                if ("test".equalsIgnoreCase(profile)) {
                    return false;
                }
            }
            return hasRequiredMysqlProperties(environment);
        }

        private boolean hasRequiredMysqlProperties(Environment environment) {
            return hasText(environment.getProperty("spring.datasource.mysql.driver-class-name"))
                    && hasText(environment.getProperty("spring.datasource.mysql.url"))
                    && hasText(environment.getProperty("spring.datasource.mysql.username"));
        }

        private boolean hasText(String value) {
            return StringUtils.hasText(value);
        }
    }

}
