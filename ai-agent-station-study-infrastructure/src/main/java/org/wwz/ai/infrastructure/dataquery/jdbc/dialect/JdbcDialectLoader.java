package org.wwz.ai.infrastructure.dataquery.jdbc.dialect;


import lombok.extern.slf4j.Slf4j;
import org.wwz.ai.domain.agent.reactor.data.exception.JdbcBizException;

import java.util.LinkedList;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

@Slf4j
public final class JdbcDialectLoader {

    private JdbcDialectLoader() {
    }


    public static JdbcDialect load(String url) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        List<JdbcDialectFactory> foundFactories = discoverFactories(cl);

        if (foundFactories.isEmpty()) {
            throw new JdbcBizException("JdbcDialectFactory无实现类");
        }

        final List<JdbcDialectFactory> matchingFactories =
                foundFactories.stream().filter(f -> f.acceptsURL(url)).toList();

        if (matchingFactories.isEmpty()) {
            throw new JdbcBizException(String.format("未找到支持%s的JdbcDialectFactory实现类", url));
        }

        return matchingFactories.get(0).create();
    }

    private static List<JdbcDialectFactory> discoverFactories(ClassLoader classLoader) {
        try {
            final List<JdbcDialectFactory> result = new LinkedList<>();
            ServiceLoader.load(JdbcDialectFactory.class, classLoader)
                    .iterator()
                    .forEachRemaining(result::add);
            return result;
        } catch (ServiceConfigurationError e) {
            throw new JdbcBizException("JdbcDialectFactory无实现类" + e.getMessage(), e);
        }
    }
}

