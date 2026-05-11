package org.wwz.ai.test.domain;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

/**
 * Reactor Mapper XML namespace 装配回归。
 * 只验证 XML 能和迁移后的 DAO namespace 正常绑定，不依赖真实业务数据库。
 */
public class ReactorMapperNamespaceBindingTest {

    @Test
    public void shouldLoadMigratedReactorMapperNamespaces() throws Exception {
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(testDataSource());
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath:/mybatis/mapper/*.xml"));
        factoryBean.setConfiguration(testConfiguration());

        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();
        Assert.assertNotNull(sqlSessionFactory);

        Configuration configuration = sqlSessionFactory.getConfiguration();
        Assert.assertTrue(configuration.hasStatement("org.wwz.ai.infrastructure.dao.reactor.IDialogueRunLedgerDao.queryByRequestId"));
        Assert.assertTrue(configuration.hasStatement("org.wwz.ai.infrastructure.dao.reactor.IDialogueSessionLedgerDao.querySessionView"));
        Assert.assertTrue(configuration.hasStatement("org.wwz.ai.infrastructure.dao.reactor.ILlmInvocationLedgerDao.queryByRunId"));
        Assert.assertTrue(configuration.hasStatement("org.wwz.ai.infrastructure.dao.reactor.IToolInvocationLedgerDao.queryByRunId"));
        Assert.assertTrue(configuration.hasStatement("org.wwz.ai.infrastructure.dao.reactor.IArtifactLedgerDao.queryByRunId"));
        Assert.assertTrue(configuration.hasStatement("org.wwz.ai.infrastructure.dao.reactor.IToolOutputImageGenerationDao.queryPageByRequestSource"));
    }

    private DataSource testDataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:reactor_mapper_binding;MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private MybatisConfiguration testConfiguration() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        return configuration;
    }
}
