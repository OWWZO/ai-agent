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
 * 锁定 Agent 上下文收敛边界。
 */
public class AgentContextConvergenceBoundaryTest {

    private static final Path PROJECT_ROOT = resolveProjectRoot();
    private static final List<String> MUST_DELETE_LEGACY_BRIDGES = List.of(
            "org.wwz.ai.domain.agent.reactor.service.IGptProcessService",
            "org.wwz.ai.domain.agent.reactor.service.IMultiAgentService",
            "org.wwz.ai.domain.agent.reactor.service.DataAgentService",
            "org.wwz.ai.domain.agent.reactor.service.Nl2SqlService"
    );
    private static final List<String> DEFERRED_LEGACY_CONTRACTS = List.of(
            "org.wwz.ai.domain.agent.reactor.config.data",
            "org.wwz.ai.domain.agent.reactor.model.imagegeneration",
            "org.wwz.ai.domain.agent.reactor.model.req",
            "org.wwz.ai.domain.agent.reactor.model.response",
            "org.wwz.ai.domain.agent.reactor.model.multi",
            "org.wwz.ai.domain.agent.reactor.model.dto",
            "org.wwz.ai.domain.agent.reactor.service",
            "org.wwz.ai.domain.agent.service.execute",
            "org.wwz.ai.domain.agent.service.armory",
            "org.wwz.ai.domain.agent.service.runtime"
    );
    private static final List<String> DEFERRED_LEGACY_MAINLINE_IMPORTS = List.of(
            "org.wwz.ai.domain.agent.reactor.config.data.DataAgentConfig",
            "org.wwz.ai.domain.agent.reactor.config.data.DataAgentConstants",
            "org.wwz.ai.domain.agent.reactor.config.data.DbConfig",
            "org.wwz.ai.domain.agent.reactor.config.data.EsConfig",
            "org.wwz.ai.domain.agent.reactor.config.data.QdrantConfig",
            "org.wwz.ai.domain.agent.reactor.model.req.AgentRequest",
            "org.wwz.ai.domain.agent.reactor.model.req.DataAgentChatReq",
            "org.wwz.ai.domain.agent.reactor.model.req.GptQueryReq",
            "org.wwz.ai.domain.agent.reactor.model.response.AgentResponse",
            "org.wwz.ai.domain.agent.reactor.model.response.GptProcessResult",
            "org.wwz.ai.domain.agent.reactor.model.multi.EventResult",
            "org.wwz.ai.domain.agent.reactor.model.multi.EventMessage",
            "org.wwz.ai.domain.agent.reactor.model.dto.FileInformation",
            "org.wwz.ai.domain.agent.reactor.model.imagegeneration.WorkspaceImageGenerationCommand",
            "org.wwz.ai.domain.agent.reactor.model.imagegeneration.WorkspaceImageGenerationResult",
            "org.wwz.ai.domain.agent.reactor.model.imagegeneration.WorkspaceImageGenerationHistoryPage",
            "org.wwz.ai.domain.agent.reactor.model.imagegeneration.WorkspaceImageGenerationHistoryBatch",
            "org.wwz.ai.domain.agent.reactor.model.imagegeneration.WorkspaceImageFile",
            "org.wwz.ai.domain.agent.reactor.model.imagegeneration.ImageGenerationExecuteCommand",
            "org.wwz.ai.domain.agent.reactor.model.imagegeneration.ImageGenerationExecutionResult",
            "org.wwz.ai.domain.agent.reactor.model.imagegeneration.ImageGenerationGatewayFile",
            "org.wwz.ai.domain.agent.reactor.model.imagegeneration.ImageGenerationGatewayRequest",
            "org.wwz.ai.domain.agent.reactor.model.imagegeneration.ImageGenerationGatewayResponse",
            "org.wwz.ai.domain.agent.reactor.service.IWorkspaceImageGenerationService",
            "org.wwz.ai.domain.agent.reactor.service.ChatModelInfoService",
            "org.wwz.ai.domain.agent.reactor.service.ChatModelSchemaService",
            "org.wwz.ai.domain.agent.reactor.service.ColumnValueSyncService",
            "org.wwz.ai.domain.agent.reactor.service.EmbeddingService",
            "org.wwz.ai.domain.agent.reactor.service.QdrantService",
            "org.wwz.ai.domain.agent.reactor.service.VectorService",
            "org.wwz.ai.domain.agent.reactor.gateway.IReactorImageGenerationGateway",
            "org.wwz.ai.domain.agent.reactor.service.imagegeneration.IImageGenerationExecutionKernel",
            "org.wwz.ai.domain.agent.reactor.service.imagegeneration.IImageGenerationBatchPersistenceService",
            "org.wwz.ai.domain.agent.service.execute.react.step.factory.DefaultReactAgentExecuteStrategyFactory",
            "org.wwz.ai.domain.agent.service.execute.planexecute.step.factory.DefaultPlanSolveAgentExecuteStrategyFactory",
            "org.wwz.ai.domain.agent.service.execute.auto1.step.factory.DefaultFlowAgentExecuteStrategyFactory",
            "org.wwz.ai.domain.agent.service.armory.node.factory.DefaultArmoryStrategyFactory",
            "org.wwz.ai.domain.agent.service.runtime.AiClientRuntimeRegistry"
    );
    private static final List<String> MUST_DELETE_LEGACY_FILES = List.of(
            "ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/IGptProcessService.java",
            "ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/IMultiAgentService.java",
            "ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/GptProcessServiceImpl.java",
            "ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/MultiAgentServiceImpl.java",
            "ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/DataAgentService.java",
            "ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/Nl2SqlService.java",
            "ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/AgentHandlerService.java",
            "ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentHandlerFactory.java",
            "ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/PlanSolveHandlerImpl.java",
            "ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/ReactHandlerImpl.java"
    );
    private static final List<String> STABLE_OWNERSHIP_ROOTS = List.of(
            "org.wwz.ai.domain.agent.runtime",
            "org.wwz.ai.domain.agent.ledger",
            "org.wwz.ai.domain.agent.memory",
            "org.wwz.ai.domain.agent.rag",
            "org.wwz.ai.domain.agent.role",
            "org.wwz.ai.domain.agent.adapter.port"
    );
    private static final Path CASE_JAVA_DIR = PROJECT_ROOT.resolve("ai-agent-station-study-case")
            .resolve("src").resolve("main").resolve("java");
    private static final Path TRIGGER_JAVA_DIR = PROJECT_ROOT.resolve("ai-agent-station-study-trigger")
            .resolve("src").resolve("main").resolve("java");
    private static final Path APP_TEST_JAVA_DIR = PROJECT_ROOT.resolve("ai-agent-station-study-app")
            .resolve("src").resolve("test").resolve("java");
    private static final Path DOMAIN_JAVA_DIR = PROJECT_ROOT.resolve("ai-agent-station-study-domain")
            .resolve("src").resolve("main").resolve("java");

    @Test
    public void shouldIntroduceCaseModuleForAgentOrchestration() {
        Assert.assertTrue(Files.exists(PROJECT_ROOT.resolve("ai-agent-station-study-case").resolve("pom.xml")));
    }

    @Test
    public void shouldProvideCaseLevelAgentDispatchContract() {
        Assert.assertTrue(Files.exists(PROJECT_ROOT.resolve("ai-agent-station-study-case")
                .resolve("src")
                .resolve("main")
                .resolve("java")
                .resolve("org")
                .resolve("wwz")
                .resolve("ai")
                .resolve("application")
                .resolve("agent")
                .resolve("dispatch")
                .resolve("IAgentDispatchService.java")));
    }

    @Test
    public void shouldKeepTriggerSideSseAdapterOutsideDomain() {
        Assert.assertTrue(Files.exists(PROJECT_ROOT.resolve("ai-agent-station-study-trigger")
                .resolve("src")
                .resolve("main")
                .resolve("java")
                .resolve("org")
                .resolve("wwz")
                .resolve("ai")
                .resolve("trigger")
                .resolve("http")
                .resolve("reactor")
                .resolve("support")
                .resolve("SseEmitterAgentSessionStream.java")));
    }

    @Test
    public void shouldProvideCaseLevelRoleAndRagSeamsForTrigger() {
        Assert.assertTrue(Files.exists(PROJECT_ROOT.resolve("ai-agent-station-study-case")
                .resolve("src").resolve("main").resolve("java")
                .resolve("org").resolve("wwz").resolve("ai").resolve("application").resolve("agent")
                .resolve("role").resolve("IFixRoleQueryService.java")));
        Assert.assertFalse(Files.exists(PROJECT_ROOT.resolve("ai-agent-station-study-case")
                .resolve("src").resolve("main").resolve("java")
                .resolve("org").resolve("wwz").resolve("ai").resolve("application").resolve("agent")
                .resolve("rag").resolve("IRagApplicationService.java")));
    }

    @Test
    public void shouldPromoteRoleRagAndMemoryToUniquePrimaryOwnership() {
        Assert.assertTrue("稳定主归属根不能为空", !STABLE_OWNERSHIP_ROOTS.isEmpty());
        Assert.assertTrue(Files.exists(PROJECT_ROOT.resolve("ai-agent-station-study-domain")
                .resolve("src").resolve("main").resolve("java")
                .resolve("org").resolve("wwz").resolve("ai").resolve("domain").resolve("agent")
                .resolve("role").resolve("IFixRoleService.java")));
        Assert.assertFalse(Files.exists(PROJECT_ROOT.resolve("ai-agent-station-study-domain")
                .resolve("src").resolve("main").resolve("java")
                .resolve("org").resolve("wwz").resolve("ai").resolve("domain").resolve("agent")
                .resolve("rag").resolve("IRagService.java")));
        Assert.assertTrue(Files.exists(PROJECT_ROOT.resolve("ai-agent-station-study-domain")
                .resolve("src").resolve("main").resolve("java")
                .resolve("org").resolve("wwz").resolve("ai").resolve("domain").resolve("agent")
                .resolve("memory").resolve("SessionContextMemoryService.java")));
        Assert.assertTrue(Files.exists(PROJECT_ROOT.resolve("ai-agent-station-study-domain")
                .resolve("src").resolve("main").resolve("java")
                .resolve("org").resolve("wwz").resolve("ai").resolve("domain").resolve("agent")
                .resolve("ledger").resolve("ExecutionLedgerQueryService.java")));
        Assert.assertTrue(Files.exists(PROJECT_ROOT.resolve("ai-agent-station-study-domain")
                .resolve("src").resolve("main").resolve("java")
                .resolve("org").resolve("wwz").resolve("ai").resolve("domain").resolve("agent")
                .resolve("runtime").resolve("agent").resolve("AgentContext.java")));
    }

    @Test
    public void shouldNotReferenceLegacyAgentServiceRootFromCaseOrTriggerMainline() throws IOException {
        assertNoLegacyRootImports(CASE_JAVA_DIR, List.of(
                "org.wwz.ai.domain.agent.service.IAgentDispatchService",
                "org.wwz.ai.domain.agent.service.IExecuteStrategy",
                "org.wwz.ai.domain.agent.service.IArmoryService",
                "org.wwz.ai.domain.agent.service.ITaskService"
        ));
        assertNoLegacyRootImports(TRIGGER_JAVA_DIR, List.of(
                "org.wwz.ai.domain.agent.service.IAgentDispatchService",
                "org.wwz.ai.domain.agent.service.IExecuteStrategy",
                "org.wwz.ai.domain.agent.service.IArmoryService",
                "org.wwz.ai.domain.agent.service.ITaskService",
                "org.wwz.ai.domain.agent.service.IFixRoleService",
                "org.wwz.ai.domain.agent.service.IRagService"
        ));
    }

    @Test
    public void shouldNotReferenceLegacyQueryAndDataAgentBridgesFromCaseMainline() throws IOException {
        Assert.assertTrue("must-delete bridge 分类不能为空", !MUST_DELETE_LEGACY_BRIDGES.isEmpty());
        assertNoLegacyRootImports(CASE_JAVA_DIR, MUST_DELETE_LEGACY_BRIDGES);
    }

    @Test
    public void shouldDocumentDeferredLegacyContractsAndStableOwnershipRoots() {
        Assert.assertTrue("允许延期的 legacy contract 分类不能为空", !DEFERRED_LEGACY_CONTRACTS.isEmpty());
        Assert.assertTrue("稳定主归属根分类不能为空", !STABLE_OWNERSHIP_ROOTS.isEmpty());
    }

    @Test
    public void shouldKeepDeferredLegacyContractsExplicitlyAllowlisted() throws IOException {
        List<String> offenders = findUnexpectedLegacyImports(
                CASE_JAVA_DIR,
                TRIGGER_JAVA_DIR,
                PROJECT_ROOT.resolve("ai-agent-station-study-app").resolve("src").resolve("main").resolve("java"),
                PROJECT_ROOT.resolve("ai-agent-station-study-infrastructure").resolve("src").resolve("main").resolve("java")
        );
        Assert.assertTrue("生产主链路出现未登记的 legacy contract 依赖: " + offenders, offenders.isEmpty());
    }

    @Test
    public void shouldKeepBoundaryTestsFocusedOnCaseSeamsInsteadOfLegacyRootInterfaces() throws IOException {
        List<String> offenders = findFilesWithLegacyServiceImports(List.of(
                PROJECT_ROOT.resolve("ai-agent-station-study-app")
                        .resolve("src").resolve("test").resolve("java")
                        .resolve("org").resolve("wwz").resolve("ai").resolve("test").resolve("domain")
                        .resolve("AgentContextConvergenceBoundaryTest.java"),
                PROJECT_ROOT.resolve("ai-agent-station-study-app")
                        .resolve("src").resolve("test").resolve("java")
                        .resolve("org").resolve("wwz").resolve("ai").resolve("test").resolve("domain")
                        .resolve("ReactorHttpControllerTest.java")
        ));
        Assert.assertTrue("边界测试不应继续以旧根接口作为主链路依赖: " + offenders, offenders.isEmpty());
    }

    @Test
    public void shouldUseConvergedRoleRagAndMemoryPackagesOnPrimaryPaths() throws IOException {
        assertNoLegacyRootImports(CASE_JAVA_DIR, List.of(
                "org.wwz.ai.domain.agent.service.IFixRoleService",
                "org.wwz.ai.domain.agent.service.IRagService"
        ));
        assertNoLegacyRootImports(APP_TEST_JAVA_DIR, List.of(
                "org.wwz.ai.domain.agent.service.IFixRoleService",
                "org.wwz.ai.domain.agent.service.IRagService",
                "org.wwz.ai.domain.agent.reactor.service.SessionContextMemoryService"
        ));
        assertNoLegacyRootImports(PROJECT_ROOT.resolve("ai-agent-station-study-infrastructure")
                .resolve("src").resolve("main").resolve("java"), List.of(
                "org.wwz.ai.domain.agent.reactor.service.SessionContextMemoryService"
        ));
    }

    @Test
    public void shouldKeepRuntimeAndLedgerOutOfLegacyReactorCatchAllPackages() {
        Path reactorJavaDir = PROJECT_ROOT.resolve("ai-agent-station-study-domain")
                .resolve("src").resolve("main").resolve("java")
                .resolve("org").resolve("wwz").resolve("ai").resolve("domain").resolve("agent")
                .resolve("reactor");
        assertNoJavaFilesUnder(reactorJavaDir.resolve("agent"));
        assertNoJavaFilesUnder(reactorJavaDir.resolve("handler"));
        assertNoJavaFilesUnder(reactorJavaDir.resolve("runtime"));
        assertNoJavaFilesUnder(reactorJavaDir.resolve("entity"));
        assertNoJavaFilesUnder(reactorJavaDir.resolve("model").resolve("ledger"));
        assertNoJavaFilesUnder(reactorJavaDir.resolve("model").resolve("memory"));
        assertNoJavaFilesUnder(reactorJavaDir.resolve("service").resolve("replay"));
        assertNoJavaFilesUnder(reactorJavaDir.resolve("service").resolve("tooloutput"));
        Assert.assertFalse(Files.exists(PROJECT_ROOT.resolve("ai-agent-station-study-domain")
                .resolve("src").resolve("main").resolve("java")
                .resolve("org").resolve("wwz").resolve("ai").resolve("domain").resolve("agent")
                .resolve("reactor").resolve("service").resolve("SessionContextMemoryService.java")));
        Assert.assertFalse(Files.exists(PROJECT_ROOT.resolve("ai-agent-station-study-domain")
                .resolve("src").resolve("main").resolve("java")
                .resolve("org").resolve("wwz").resolve("ai").resolve("domain").resolve("agent")
                .resolve("reactor").resolve("service").resolve("SchemaRecallService.java")));
        Assert.assertFalse(Files.exists(PROJECT_ROOT.resolve("ai-agent-station-study-domain")
                .resolve("src").resolve("main").resolve("java")
                .resolve("org").resolve("wwz").resolve("ai").resolve("domain").resolve("agent")
                .resolve("reactor").resolve("service").resolve("SopRecallService.java")));
        Assert.assertFalse(Files.exists(PROJECT_ROOT.resolve("ai-agent-station-study-domain")
                .resolve("src").resolve("main").resolve("java")
                .resolve("org").resolve("wwz").resolve("ai").resolve("domain").resolve("agent")
                .resolve("reactor").resolve("service").resolve("TableRagService.java")));
    }

    @Test
    public void shouldDeleteMandatoryLegacyQueryAndDataAgentBridgeFiles() {
        for (String legacyFile : MUST_DELETE_LEGACY_FILES) {
            assertPathMissing(legacyFile);
        }
    }

    @Test
    public void shouldKeepDeferredLegacyDirectoriesDocumentedWithPackageInfo() {
        assertPathExists("ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/config/data/package-info.java");
        assertPathExists("ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/package-info.java");
        assertPathExists("ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/package-info.java");
        assertPathExists("ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/imagegeneration/package-info.java");
        assertPathExists("ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/execute/package-info.java");
        assertPathExists("ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/armory/package-info.java");
        assertPathExists("ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/runtime/package-info.java");
    }

    @Test
    public void shouldRemoveSseEmitterAndLegacySseHelpersFromDomain() throws IOException {
        List<String> emitterOffenders = findFilesContaining(DOMAIN_JAVA_DIR, "SseEmitter");
        Assert.assertTrue("domain 中不应再出现 SseEmitter: " + emitterOffenders, emitterOffenders.isEmpty());

        List<String> helperOffenders = findFilesContaining(DOMAIN_JAVA_DIR, "SseEmitterUTF8");
        Assert.assertTrue("domain 中不应再保留旧 SSE helper: " + helperOffenders, helperOffenders.isEmpty());

        Assert.assertFalse(Files.exists(PROJECT_ROOT.resolve("ai-agent-station-study-domain")
                .resolve("src").resolve("main").resolve("java")
                .resolve("org").resolve("wwz").resolve("ai").resolve("domain").resolve("agent")
                .resolve("reactor").resolve("util").resolve("SseUtil.java")));
        Assert.assertFalse(Files.exists(PROJECT_ROOT.resolve("ai-agent-station-study-domain")
                .resolve("src").resolve("main").resolve("java")
                .resolve("org").resolve("wwz").resolve("ai").resolve("domain").resolve("agent")
                .resolve("reactor").resolve("util").resolve("SseEmitterUTF8.java")));
        Assert.assertFalse(Files.exists(PROJECT_ROOT.resolve("ai-agent-station-study-domain")
                .resolve("src").resolve("main").resolve("java")
                .resolve("org").resolve("wwz").resolve("ai").resolve("domain").resolve("agent")
                .resolve("runtime").resolve("printer").resolve("SSEPrinter.java")));
    }

    @Test
    public void shouldRemoveLegacyRuntimeExecutorsFromDomain() throws IOException {
        List<String> okHttpOffenders = findFilesContaining(DOMAIN_JAVA_DIR, "new OkHttpClient");
        Assert.assertTrue("domain 中不应再直接创建 OkHttpClient: " + okHttpOffenders, okHttpOffenders.isEmpty());

        List<String> jdbcProviderOffenders = findFilesContaining(DOMAIN_JAVA_DIR, "JdbcDataProvider");
        Assert.assertTrue("domain 中不应再直接依赖 JdbcDataProvider: " + jdbcProviderOffenders, jdbcProviderOffenders.isEmpty());

        Assert.assertFalse(Files.exists(PROJECT_ROOT.resolve("ai-agent-station-study-domain")
                .resolve("src").resolve("main").resolve("java")
                .resolve("org").resolve("wwz").resolve("ai").resolve("domain").resolve("agent")
                .resolve("runtime").resolve("util").resolve("OkHttpUtil.java")));
    }

    @Test
    public void shouldRemoveLegacyServiceLocatorAccessFromDomain() throws IOException {
        List<String> springContextOffenders = findFilesContaining(DOMAIN_JAVA_DIR, "SpringContextHolder");
        Assert.assertTrue("domain 中不应再引用 SpringContextHolder: " + springContextOffenders,
                springContextOffenders.isEmpty());

        List<String> getBeanOffenders = findFilesContaining(DOMAIN_JAVA_DIR, "applicationContext.getBean(");
        Assert.assertTrue("domain 中不应再直接调用 applicationContext.getBean(...): " + getBeanOffenders,
                getBeanOffenders.isEmpty());
    }

    @Test
    public void shouldRemoveUnmanagedMainlineAsyncEntrypoints() throws IOException {
        List<String> threadUtilOffenders = findFilesContaining(
                DOMAIN_JAVA_DIR,
                "ThreadUtil.execute("
        );
        Assert.assertTrue("主链路不应再使用 ThreadUtil.execute(...): " + threadUtilOffenders, threadUtilOffenders.isEmpty());

        List<String> commonPoolOffenders = findFilesContaining(
                DOMAIN_JAVA_DIR,
                "CompletableFuture.supplyAsync(() ->"
        );
        commonPoolOffenders.removeIf(path -> path.endsWith("AgentExecutorSupport.java"));
        Assert.assertTrue("主链路不应再使用默认 common pool: " + commonPoolOffenders, commonPoolOffenders.isEmpty());
    }

    private void assertNoLegacyRootImports(Path root, List<String> bannedImports) throws IOException {
        for (String bannedImport : bannedImports) {
            List<String> offenders = findFilesContaining(root, "import " + bannedImport + ";");
            Assert.assertTrue("不应继续依赖旧根接口 " + bannedImport + ": " + offenders, offenders.isEmpty());
        }
    }

    private List<String> findFilesContaining(Path root, String needle) throws IOException {
        if (!Files.exists(root)) {
            return List.of();
        }
        try (Stream<Path> pathStream = Files.walk(root)) {
            return pathStream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> fileContains(path, needle))
                    .map(path -> PROJECT_ROOT.relativize(path).toString().replace('\\', '/'))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    private List<String> findFilesWithLegacyServiceImports(List<Path> files) {
        return files.stream()
                .filter(Files::exists)
                .filter(this::containsLegacyServiceImportLine)
                .map(path -> PROJECT_ROOT.relativize(path).toString().replace('\\', '/'))
                .sorted()
                .collect(Collectors.toList());
    }

    private void assertNoJavaFilesUnder(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> pathStream = Files.walk(root)) {
            List<String> offenders = pathStream
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(path -> PROJECT_ROOT.relativize(path).toString().replace('\\', '/'))
                    .sorted()
                    .collect(Collectors.toList());
            Assert.assertTrue("旧 reactor 迁移目录不应再承载 Java 主逻辑: " + offenders, offenders.isEmpty());
        } catch (IOException e) {
            throw new IllegalStateException("读取目录失败: " + root, e);
        }
    }

    private void assertPathMissing(String relativePath) {
        Assert.assertFalse("遗留 bridge 文件必须删除: " + relativePath,
                Files.exists(PROJECT_ROOT.resolve(relativePath.replace('/', '\\'))));
    }

    private void assertPathExists(String relativePath) {
        Assert.assertTrue("延期 legacy 目录必须显式说明边界: " + relativePath,
                Files.exists(PROJECT_ROOT.resolve(relativePath.replace('/', '\\'))));
    }

    private List<String> findUnexpectedLegacyImports(Path... roots) throws IOException {
        List<String> expectedImportLines = DEFERRED_LEGACY_MAINLINE_IMPORTS.stream()
                .map(value -> "import " + value + ";")
                .collect(Collectors.toList());
        List<String> legacyRootPrefixes = DEFERRED_LEGACY_CONTRACTS.stream()
                .map(value -> "import " + value + ".")
                .collect(Collectors.toList());
        try (Stream<Path> rootStream = Stream.of(roots)) {
            return rootStream
                    .filter(Files::exists)
                    .flatMap(root -> {
                        try {
                            return Files.walk(root);
                        } catch (IOException e) {
                            throw new IllegalStateException("读取目录失败: " + root, e);
                        }
                    })
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> containsUnexpectedLegacyImport(path, legacyRootPrefixes, expectedImportLines))
                    .map(path -> PROJECT_ROOT.relativize(path).toString().replace('\\', '/'))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    private boolean containsUnexpectedLegacyImport(Path path,
                                                   List<String> legacyRootPrefixes,
                                                   List<String> expectedImportLines) {
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8).stream()
                    .map(String::trim)
                    .filter(line -> line.startsWith("import "))
                    .anyMatch(line -> isUnexpectedLegacyImport(line, legacyRootPrefixes, expectedImportLines));
        } catch (IOException e) {
            throw new IllegalStateException("读取文件失败: " + path, e);
        }
    }

    private boolean isUnexpectedLegacyImport(String line,
                                             List<String> legacyRootPrefixes,
                                             List<String> expectedImportLines) {
        boolean matchesLegacyRoot = legacyRootPrefixes.stream().anyMatch(line::startsWith);
        if (!matchesLegacyRoot) {
            return false;
        }
        return !expectedImportLines.contains(line);
    }

    private boolean containsLegacyServiceImportLine(Path path) {
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8).stream()
                    .map(String::trim)
                    .anyMatch(line -> line.startsWith("import org.wwz.ai.domain.agent.service."));
        } catch (IOException e) {
            throw new IllegalStateException("读取文件失败: " + path, e);
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

    private static Path resolveProjectRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml"))
                    && Files.exists(current.resolve("ai-agent-station-study-domain"))
                    && Files.exists(current.resolve("ai-agent-station-study-trigger"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("无法定位仓库根目录");
    }
}
