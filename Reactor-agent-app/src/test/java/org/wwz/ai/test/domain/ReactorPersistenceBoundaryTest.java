package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Reactor Phase 2A 持久化边界结构检查。
 */
public class ReactorPersistenceBoundaryTest {

    private static final Path PROJECT_ROOT = resolveProjectRoot();
    private static final Path DOMAIN_ROOT = PROJECT_ROOT.resolve("ai-agent-station-study-domain/src/main/java");
    private static final Path MAPPER_ROOT = PROJECT_ROOT.resolve("ai-agent-station-study-app/src/main/resources/mybatis/mapper");

    @Test
    public void shouldRemoveReactorMapperOwnershipFromDomain() throws Exception {
        Path legacyMapperDir = DOMAIN_ROOT.resolve("org/wwz/ai/domain/agent/reactor/mapper");
        Path legacySessionMemoryImpl = DOMAIN_ROOT.resolve("org/wwz/ai/domain/agent/reactor/service/impl/SessionContextMemoryServiceImpl.java");
        Path legacyWorkspaceImageImpl = DOMAIN_ROOT.resolve("org/wwz/ai/domain/agent/reactor/service/impl/WorkspaceImageGenerationServiceImpl.java");
        Assert.assertFalse("domain 不应再保留 reactor mapper 目录", Files.exists(legacyMapperDir));
        Assert.assertFalse("domain 不应再保留 SessionContextMemoryServiceImpl 技术实现", Files.exists(legacySessionMemoryImpl));
        Assert.assertFalse("domain 不应再保留 WorkspaceImageGenerationServiceImpl 技术实现", Files.exists(legacyWorkspaceImageImpl));
        assertNoContent(DOMAIN_ROOT, "@Mapper");
        assertNoContent(DOMAIN_ROOT, "BaseMapper<");
        assertNoContent(DOMAIN_ROOT, "org.wwz.ai.domain.agent.reactor.mapper");
    }

    @Test
    public void shouldBindMapperXmlToInfrastructureDaoNamespace() throws Exception {
        assertNoContent(MAPPER_ROOT, "org.wwz.ai.domain.agent.reactor.mapper.");
        assertContains(MAPPER_ROOT.resolve("dialogue_run_ledger_mapper.xml"), "org.wwz.ai.infrastructure.dao.reactor.IDialogueRunLedgerDao");
        assertContains(MAPPER_ROOT.resolve("artifact_ledger_mapper.xml"), "org.wwz.ai.infrastructure.dao.reactor.IArtifactLedgerDao");
        assertContains(MAPPER_ROOT.resolve("tool_output_image_generation_mapper.xml"), "org.wwz.ai.infrastructure.dao.reactor.IToolOutputImageGenerationDao");
    }

    private void assertNoContent(Path root, String expectedAbsent) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            List<Path> files = paths
                    .filter(Files::isRegularFile)
                    .toList();
            for (Path file : files) {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                Assert.assertFalse(file + " 不应包含: " + expectedAbsent, content.contains(expectedAbsent));
            }
        }
    }

    private void assertContains(Path file, String expectedContent) throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        Assert.assertTrue(file + " 应包含: " + expectedContent, content.contains(expectedContent));
    }

    /**
     * app 模块下执行测试时，工作目录会落在模块根而不是仓库根，需要向上回溯定位真实项目根目录。
     */
    private static Path resolveProjectRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("ai-agent-station-study-domain"))
                    && Files.exists(current.resolve("ai-agent-station-study-infrastructure"))
                    && Files.exists(current.resolve("ai-agent-station-study-app"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("无法定位项目根目录");
    }
}
