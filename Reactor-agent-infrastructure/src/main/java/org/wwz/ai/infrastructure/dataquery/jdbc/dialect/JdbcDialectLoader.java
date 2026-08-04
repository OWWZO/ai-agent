package org.wwz.ai.infrastructure.dataquery.jdbc.dialect;


import lombok.extern.slf4j.Slf4j;
import org.wwz.ai.domain.agent.reactor.data.exception.JdbcBizException;

import java.util.LinkedList;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/** 根据 JDBC URL 通过 SPI 选择唯一数据库方言实现。 */
@Slf4j
public final class JdbcDialectLoader {

    private JdbcDialectLoader() {
    }


    public static JdbcDialect load(String url) {
        // URL 是方言选择的唯一输入，匹配后由工厂创建无状态方言实例。
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
        // SPI 发现失败属于基础设施装配错误，统一转换为可识别的 JDBC 业务异常。
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
