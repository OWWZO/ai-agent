package org.wwz.ai.infrastructure.dataquery.jdbc.catalog;


import lombok.extern.slf4j.Slf4j;
import org.wwz.ai.domain.agent.reactor.data.exception.CatalogException;
import org.wwz.ai.domain.agent.reactor.data.exception.JdbcBizException;
import org.wwz.ai.infrastructure.dataquery.jdbc.dialect.DialectEnum;

import java.util.LinkedList;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * JDBC 元数据目录的 SPI 加载器。
 *
 * <p>实现通过 {@link ServiceLoader} 发现，并要求一个方言只能对应一个目录工厂；
 * 没有实现或出现重复实现都视为装配错误，避免运行时静默选择不确定的元数据规则。</p>
 */
@Slf4j
public final class JdbcCatalogLoader {
    private JdbcCatalogLoader() {
    }


    public static JdbcCatalog load(DialectEnum jdbcDialect) {
        // 先发现全部工厂，再按方言精确匹配，确保目录选择与连接方言保持一致。
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        List<JdbcCatalogFactory> foundFactories = discoverFactories(cl);

        if (foundFactories.isEmpty()) {
            throw new JdbcBizException(String.format(
                    "Could not find any jdbc dialect factories that implement '%s' in the classpath.",
                    JdbcCatalog.class.getName()));
        }

        final List<JdbcCatalogFactory> matchingFactories =
                foundFactories.stream().filter(f -> f.jdbcDialect() == jdbcDialect).toList();
        if (matchingFactories.isEmpty()) {
            throw new CatalogException("Could not find any jdbc dialect factory that can handled ");
        }
        if (matchingFactories.size() > 1) {
            throw new JdbcBizException("Multiple jdbc dialect factories can handle");
        }

        return matchingFactories.get(0).createCatalog();
    }


    private static List<JdbcCatalogFactory> discoverFactories(ClassLoader classLoader) {
        // SPI 配置损坏时转换为业务异常，调用方可以按数据源装配失败处理。
        try {
            final List<JdbcCatalogFactory> result = new LinkedList<>();
            ServiceLoader.load(JdbcCatalogFactory.class, classLoader)
                    .iterator()
                    .forEachRemaining(result::add);
            return result;
        } catch (ServiceConfigurationError e) {
            log.error("Could not load service provider for Catalog factory.", e);
            throw new JdbcBizException(
                    "Could not load service provider for Catalog factory.", e);
        }
    }
}
