package org.wwz.ai.infrastructure.dataquery.jdbc.catalog;


import lombok.extern.slf4j.Slf4j;
import org.wwz.ai.domain.agent.reactor.data.exception.CatalogException;
import org.wwz.ai.domain.agent.reactor.data.exception.JdbcBizException;
import org.wwz.ai.infrastructure.dataquery.jdbc.dialect.DialectEnum;

import java.util.LinkedList;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

@Slf4j
public final class JdbcCatalogLoader {
    private JdbcCatalogLoader() {
    }


    public static JdbcCatalog load(DialectEnum jdbcDialect) {
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

