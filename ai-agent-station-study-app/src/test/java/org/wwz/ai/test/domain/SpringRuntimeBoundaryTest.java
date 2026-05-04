package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 锁定 Phase 2B 的 Spring 运行时边界，避免 service locator 回流到 domain。
 */
public class SpringRuntimeBoundaryTest {

    private static final Path PROJECT_ROOT = resolveProjectRoot();
    private static final Path DOMAIN_JAVA_DIR = PROJECT_ROOT
            .resolve("ai-agent-station-study-domain")
            .resolve("src")
            .resolve("main")
            .resolve("java");

    @Test
    public void shouldRemoveSpringContextHolderFromDomainRuntime() throws IOException {
        List<String> offenders = findFilesContaining("SpringContextHolder");
        Assert.assertTrue(
                "domain 运行时不应再引用 SpringContextHolder: " + offenders,
                offenders.isEmpty()
        );
    }

    @Test
    public void shouldRemoveDirectApplicationContextGetBeanCallsFromDomainRuntime() throws IOException {
        List<String> offenders = findFilesContaining("applicationContext.getBean(");
        Assert.assertTrue(
                "domain 运行时不应再直接调用 applicationContext.getBean(...): " + offenders,
                offenders.isEmpty()
        );
    }

    @Test
    public void shouldMoveAgentHandlerConfigOutOfDomainModule() {
        Path domainHandlerConfig = DOMAIN_JAVA_DIR
                .resolve("org")
                .resolve("wwz")
                .resolve("ai")
                .resolve("domain")
                .resolve("agent")
                .resolve("reactor")
                .resolve("handler")
                .resolve("AgentHandlerConfig.java");
        Assert.assertFalse(
                "AgentHandlerConfig 必须迁出 domain 模块",
                Files.exists(domainHandlerConfig)
        );
    }

    private List<String> findFilesContaining(String needle) throws IOException {
        try (Stream<Path> pathStream = Files.walk(DOMAIN_JAVA_DIR)) {
            return pathStream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> fileContains(path, needle))
                    .map(path -> PROJECT_ROOT.relativize(path).toString().replace('\\', '/'))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    private boolean fileContains(Path path, String needle) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            return content.contains(needle);
        } catch (IOException e) {
            throw new IllegalStateException("读取文件失败: " + path, e);
        }
    }

    /**
     * app 模块下执行测试时，工作目录会落在模块根而不是仓库根，需要向上回溯定位真实项目根目录。
     */
    private static Path resolveProjectRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("ai-agent-station-study-domain"))
                    && Files.exists(current.resolve("ai-agent-station-study-app"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("无法定位项目根目录");
    }
}
