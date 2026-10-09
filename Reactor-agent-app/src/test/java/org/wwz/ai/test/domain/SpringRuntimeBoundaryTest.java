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

    private static final Path PROJECT_ROOT = BoundaryTestPaths.projectRoot();
    private static final Path DOMAIN_JAVA_DIR = BoundaryTestPaths.moduleMainJava("Reactor-agent-domain");

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

    @Test
    public void shouldKeepSseProtocolOnlyInTriggerSideAdapters() throws IOException {
        List<String> offenders = findFilesContaining("SseEmitter");
        Assert.assertTrue(
                "domain 中不应再保留 SSE 协议对象: " + offenders,
                offenders.isEmpty()
        );
    }

    @Test
    public void shouldRemoveDirectOkHttpClientCreationFromDomainRuntime() throws IOException {
        List<String> offenders = findFilesContaining("new OkHttpClient");
        Assert.assertTrue(
                "domain 运行时不应再直接创建 OkHttpClient: " + offenders,
                offenders.isEmpty()
        );
    }

    @Test
    public void shouldRemoveJdbcProvidersFromDomainRuntime() throws IOException {
        List<String> offenders = findFilesContaining("JdbcDataProvider");
        Assert.assertTrue(
                "domain 运行时不应再直接依赖 JdbcDataProvider: " + offenders,
                offenders.isEmpty()
        );
    }

    @Test
    public void shouldKeepLegacyExecuteAndArmoryPackagesInsideCaseAndDomainOnly() throws IOException {
        assertNoImportsFrom(
                BoundaryTestPaths.moduleMainJava("Reactor-agent-trigger"),
                "org.wwz.ai.domain.agent.service.execute.",
                "org.wwz.ai.domain.agent.service.armory.",
                "org.wwz.ai.domain.agent.service.runtime."
        );
        assertNoImportsFrom(
                BoundaryTestPaths.moduleMainJava("Reactor-agent-app"),
                "org.wwz.ai.domain.agent.service.execute.",
                "org.wwz.ai.domain.agent.service.armory.",
                "org.wwz.ai.domain.agent.service.runtime."
        );
        assertNoImportsFrom(
                BoundaryTestPaths.moduleMainJava("Reactor-agent-infrastructure"),
                "org.wwz.ai.domain.agent.service.execute.",
                "org.wwz.ai.domain.agent.service.armory.",
                "org.wwz.ai.domain.agent.service.runtime."
        );
    }

    @Test
    public void shouldKeepTriggerFreeOfInfrastructureImports() throws IOException {
        assertNoImportsFrom(
                BoundaryTestPaths.moduleMainJava("Reactor-agent-trigger"),
                "org.wwz.ai.infrastructure."
        );
    }

    @Test
    public void shouldKeepCaseFreeOfInfrastructureImports() throws IOException {
        assertNoImportsFrom(
                BoundaryTestPaths.moduleMainJava("Reactor-agent-case"),
                "org.wwz.ai.infrastructure."
        );
    }

    @Test
    public void shouldKeepDomainFreeOfPersistenceAndTechnologyImports() throws IOException {
        List<String> daoOrMapperImports = findFilesWithDaoOrMapperImports();
        Assert.assertTrue("domain 不应 import DAO 或 Mapper: " + daoOrMapperImports,
                daoOrMapperImports.isEmpty());
        assertNoImportsFrom(
                DOMAIN_JAVA_DIR,
                "org.apache.ibatis.",
                "com.baomidou.",
                "org.elasticsearch.",
                "io.qdrant.",
                "okhttp3.",
                "org.springframework.ai."
        );
    }

    @Test
    public void shouldKeepMcpSdkCallbacksAndWebFluxOutsideDomain() throws IOException {
        assertNoImportsFrom(
                DOMAIN_JAVA_DIR,
                "io.modelcontextprotocol.",
                "org.springframework.ai.",
                "org.springframework.web.reactive."
        );

        Path domainMcpRuntime = DOMAIN_JAVA_DIR
                .resolve("org/wwz/ai/domain/agent/runtime/tool/mcp/runtime");
        Assert.assertFalse("MCP 技术运行时必须迁出 domain", containsJavaSource(domainMcpRuntime));

        Path infrastructureMcpRoot = BoundaryTestPaths.moduleMainJava("Reactor-agent-infrastructure")
                .resolve("org/wwz/ai/infrastructure/mcp");
        Assert.assertTrue("Infrastructure 必须承载 MCP 实现", Files.isDirectory(infrastructureMcpRoot));
        Assert.assertTrue("Infrastructure 必须承载 MCP registry",
                Files.exists(infrastructureMcpRoot.resolve("registry/McpRegistry.java")));
        Assert.assertTrue("Infrastructure 必须承载 MCP transport",
                Files.exists(infrastructureMcpRoot.resolve("transport/McpTransportFactory.java")));
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

    private void assertNoImportsFrom(Path root, String... importPrefixes) throws IOException {
        try (Stream<Path> pathStream = Files.walk(root)) {
            List<String> offenders = pathStream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> containsAnyImportPrefix(path, importPrefixes))
                    .map(path -> PROJECT_ROOT.relativize(path).toString().replace('\\', '/'))
                    .sorted()
                    .collect(Collectors.toList());
            Assert.assertTrue("源码导入边界违规: " + offenders,
                    offenders.isEmpty());
        }
    }

    private boolean containsAnyImportPrefix(Path path, String... importPrefixes) {
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8).stream()
                    .map(this::importedName)
                    .filter(importedName -> !importedName.isEmpty())
                    .anyMatch(importedName -> {
                        for (String importPrefix : importPrefixes) {
                            if (importedName.startsWith(importPrefix)) {
                                return true;
                            }
                        }
                        return false;
                    });
        } catch (IOException e) {
            throw new IllegalStateException("读取文件失败: " + path, e);
        }
    }

    private List<String> findFilesWithDaoOrMapperImports() throws IOException {
        try (Stream<Path> pathStream = Files.walk(DOMAIN_JAVA_DIR)) {
            return pathStream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(this::containsDaoOrMapperImport)
                    .map(path -> PROJECT_ROOT.relativize(path).toString().replace('\\', '/'))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    private boolean containsDaoOrMapperImport(Path path) {
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8).stream()
                    .map(this::importedName)
                    .filter(importedName -> !importedName.isEmpty())
                    .anyMatch(importedName -> {
                        String[] segments = importedName.split("\\.");
                        String simpleName = segments[segments.length - 1];
                        boolean persistencePackage = false;
                        for (String segment : segments) {
                            if (segment.equalsIgnoreCase("dao") || segment.equalsIgnoreCase("mapper")) {
                                persistencePackage = true;
                                break;
                            }
                        }
                        return persistencePackage
                                || simpleName.endsWith("Dao")
                                || (simpleName.endsWith("Mapper")
                                && importedName.startsWith("org.apache.ibatis."));
                    });
        } catch (IOException e) {
            throw new IllegalStateException("读取文件失败: " + path, e);
        }
    }

    private String importedName(String line) {
        String trimmed = line.trim();
        if (!trimmed.startsWith("import ")) {
            return "";
        }
        String importedName = trimmed.substring("import ".length()).trim();
        if (importedName.startsWith("static ")) {
            importedName = importedName.substring("static ".length());
        }
        return importedName.endsWith(";")
                ? importedName.substring(0, importedName.length() - 1)
                : importedName;
    }

    private boolean fileContains(Path path, String needle) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            return content.contains(needle);
        } catch (IOException e) {
            throw new IllegalStateException("读取文件失败: " + path, e);
        }
    }

    private boolean containsJavaSource(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return false;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.anyMatch(path -> Files.isRegularFile(path)
                    && path.toString().endsWith(".java"));
        }
    }

}
