package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reactor 运行时边界结构测试。
 * 先用源码扫描锁死禁止项，避免运行时解耦回退到 Spring Service Locator。
 */
public class ReactorRuntimeBoundaryTest {

    private static final Path DOMAIN_MAIN_JAVA = Path.of("src/main/java");
    private static final Path DOMAIN_MODULE_ROOT = Path.of("").toAbsolutePath();

    @Test
    public void shouldRemoveSpringContextHolderFromDomainRuntime() throws IOException {
        List<String> offenders = findFilesContaining("SpringContextHolder");
        Assert.assertTrue(
                "domain 运行时不应再引用 SpringContextHolder: " + offenders,
                offenders.isEmpty()
        );
    }

    @Test
    public void shouldRemoveDirectApplicationContextGetBeanFromDomainRuntime() throws IOException {
        List<String> offenders = findFilesContaining("getBean(");
        Assert.assertTrue(
                "domain 运行时不应再直接调用 ApplicationContext.getBean(...): " + offenders,
                offenders.isEmpty()
        );
    }

    @Test
    public void shouldMoveAgentHandlerConfigOutOfDomainModule() {
        Path configPath = DOMAIN_MODULE_ROOT.resolve(
                "src/main/java/org/wwz/ai/domain/agent/reactor/handler/AgentHandlerConfig.java");
        Assert.assertFalse("AgentHandlerConfig 应迁移到 app 模块，不能继续留在 domain", Files.exists(configPath));
    }

    @Test
    public void shouldOnlyKeepApprovedTransitionalConfigurationResidueInDomain() throws IOException {
        List<String> configurationFiles = findAnnotationDeclarations("@Configuration");
        List<String> beanFiles = findAnnotationDeclarations("@Bean");

        Set<String> expectedConfigurationFiles = new LinkedHashSet<>();
        expectedConfigurationFiles.add("src/main/java/org/wwz/ai/domain/agent/reactor/config/ReactorConfig.java");

        Assert.assertEquals(
                "domain 中只允许保留过渡态 ReactorConfig 的 @Configuration 声明",
                expectedConfigurationFiles,
                new LinkedHashSet<>(configurationFiles)
        );
        Assert.assertTrue("domain 中不应再保留 @Bean 运行时装配声明: " + beanFiles, beanFiles.isEmpty());
    }

    private List<String> findFilesContaining(String pattern) throws IOException {
        try (Stream<Path> stream = Files.walk(DOMAIN_MODULE_ROOT.resolve(DOMAIN_MAIN_JAVA))) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> containsPattern(path, pattern))
                    .map(path -> DOMAIN_MODULE_ROOT.relativize(path).toString().replace('\\', '/'))
                    .sorted(Comparator.naturalOrder())
                    .collect(Collectors.toList());
        }
    }

    private List<String> findAnnotationDeclarations(String annotation) throws IOException {
        try (Stream<Path> stream = Files.walk(DOMAIN_MODULE_ROOT.resolve(DOMAIN_MAIN_JAVA))) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> containsAnnotationDeclaration(path, annotation))
                    .map(path -> DOMAIN_MODULE_ROOT.relativize(path).toString().replace('\\', '/'))
                    .sorted(Comparator.naturalOrder())
                    .collect(Collectors.toList());
        }
    }

    private boolean containsPattern(Path path, String pattern) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            return content.contains(pattern);
        } catch (IOException e) {
            throw new RuntimeException("读取源码失败: " + path, e);
        }
    }

    private boolean containsAnnotationDeclaration(Path path, String annotation) {
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8).stream()
                    .map(String::trim)
                    .anyMatch(line -> line.equals(annotation) || line.startsWith(annotation + "("));
        } catch (IOException e) {
            throw new RuntimeException("读取源码失败: " + path, e);
        }
    }
}
