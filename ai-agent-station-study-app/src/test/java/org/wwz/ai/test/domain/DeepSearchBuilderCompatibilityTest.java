package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.dto.DeepSearchrResponse;
import org.wwz.ai.domain.agent.runtime.tool.common.DeepSearchStructuredResultBuilder;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * deep_search 结构化输出兼容性回归。
 * 模拟运行时缺失 Lombok Builder 内部类时，构建结果仍然可用。
 */
public class DeepSearchBuilderCompatibilityTest {

    @Test
    public void shouldBuildPayloadWhenLombokBuilderInnerClassesAreUnavailable() throws Exception {
        ClassLoader isolatedLoader = new DeepSearchCompatibilityClassLoader(
                new URL[]{DeepSearchStructuredResultBuilder.class.getProtectionDomain().getCodeSource().getLocation()},
                getClass().getClassLoader()
        );
        Class<?> builderClass = isolatedLoader.loadClass(DeepSearchStructuredResultBuilder.class.getName());
        Class<?> responseClass = isolatedLoader.loadClass(DeepSearchrResponse.class.getName());
        Class<?> searchResultClass = isolatedLoader.loadClass(DeepSearchrResponse.SearchResult.class.getName());
        Class<?> searchDocClass = isolatedLoader.loadClass(DeepSearchrResponse.SearchDoc.class.getName());

        Object builder = builderClass.getConstructor(String.class).newInstance("新能源车出口趋势");
        Method recordEvent = builderClass.getMethod("recordEvent", responseClass);
        Method recordFinalAnswer = builderClass.getMethod("recordFinalAnswer", String.class, String.class);
        Method buildPayload = builderClass.getMethod("buildPayload", String.class);

        Object searchDoc = searchDocClass.getConstructor(String.class, String.class, String.class, String.class)
                .newInstance("web", repeat("出口量持续增长，", 12), "海关总署：出口量创新高", "https://example.com/customs");
        Object extendEvent = responseClass
                .getConstructor(String.class, String.class, String.class, searchResultClass, Boolean.class, Boolean.class, String.class)
                .newInstance(
                        null,
                        "新能源车出口趋势",
                        null,
                        searchResultClass.getConstructor(List.class, List.class)
                                .newInstance(List.of("中国新能源车出口数据"), null),
                        Boolean.FALSE,
                        Boolean.FALSE,
                        "extend"
                );
        Object searchEvent = responseClass
                .getConstructor(String.class, String.class, String.class, searchResultClass, Boolean.class, Boolean.class, String.class)
                .newInstance(
                        null,
                        "新能源车出口趋势",
                        null,
                        searchResultClass.getConstructor(List.class, List.class)
                                .newInstance(List.of("中国新能源车出口数据"), List.of(List.of(searchDoc))),
                        Boolean.FALSE,
                        Boolean.FALSE,
                        "search"
                );

        recordEvent.invoke(builder, extendEvent);
        recordEvent.invoke(builder, searchEvent);
        recordFinalAnswer.invoke(builder, "新能源车出口趋势", "综合多个来源，新能源车出口继续增长。");
        Object payload = buildPayload.invoke(builder, "fallback");

        Method getStructuredOutput = payload.getClass().getMethod("getStructuredOutput");
        Method getLlmObservation = payload.getClass().getMethod("getLlmObservation");

        Assert.assertNotNull(getStructuredOutput.invoke(payload));
        Assert.assertTrue(String.valueOf(getLlmObservation.invoke(payload)).contains("中国新能源车出口数据"));
    }

    private static String repeat(String part, int count) {
        StringBuilder builder = new StringBuilder();
        for (int idx = 0; idx < count; idx++) {
            builder.append(part);
        }
        return builder.toString();
    }

    /**
     * 通过子优先类加载器模拟运行时缺失 Lombok 生成的 Builder 内部类。
     */
    private static final class DeepSearchCompatibilityClassLoader extends URLClassLoader {

        private static final Set<String> BLOCKED_CLASS_NAMES = new LinkedHashSet<>(List.of(
                "org.wwz.ai.domain.agent.ledger.model.tooloutput.DeepSearchStage$DeepSearchStageBuilder",
                "org.wwz.ai.domain.agent.ledger.model.tooloutput.DeepSearchToolOutput$DeepSearchToolOutputBuilder",
                "org.wwz.ai.domain.agent.ledger.model.tooloutput.DeepSearchQueryResult$DeepSearchQueryResultBuilder",
                "org.wwz.ai.domain.agent.ledger.model.tooloutput.DeepSearchDoc$DeepSearchDocBuilder"
        ));

        private static final List<String> CHILD_FIRST_PREFIXES = List.of(
                "org.wwz.ai.domain.agent.runtime.tool.common.DeepSearchStructuredResultBuilder",
                "org.wwz.ai.domain.agent.runtime.dto.DeepSearchrResponse",
                "org.wwz.ai.domain.agent.ledger.model.tooloutput.DeepSearch"
        );

        private DeepSearchCompatibilityClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (BLOCKED_CLASS_NAMES.contains(name)) {
                throw new ClassNotFoundException("blocked by compatibility test: " + name);
            }
            if (shouldLoadChildFirst(name)) {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loadedClass = findLoadedClass(name);
                    if (loadedClass == null) {
                        try {
                            loadedClass = findClass(name);
                        } catch (ClassNotFoundException ignored) {
                            loadedClass = super.loadClass(name, false);
                        }
                    }
                    if (resolve) {
                        resolveClass(loadedClass);
                    }
                    return loadedClass;
                }
            }
            return super.loadClass(name, resolve);
        }

        private boolean shouldLoadChildFirst(String className) {
            for (String prefix : CHILD_FIRST_PREFIXES) {
                if (className.startsWith(prefix)) {
                    return true;
                }
            }
            return false;
        }
    }
}
