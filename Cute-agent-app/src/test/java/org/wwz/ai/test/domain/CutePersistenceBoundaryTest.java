package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.rag.model.chatmodel.ChatModelInfo;
import org.wwz.ai.domain.agent.rag.model.chatmodel.ChatModelSchema;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Cute Phase 2A 持久化边界结构检查。
 */
public class CutePersistenceBoundaryTest {

    private static final Path DOMAIN_ROOT = BoundaryTestPaths.moduleMainJava("Cute-agent-domain");
    private static final Path MAPPER_ROOT = BoundaryTestPaths.requireDirectory(
            BoundaryTestPaths.moduleRoot("Cute-agent-app")
                    .resolve("src/main/resources/mybatis/mapper")
    );

    @Test
    public void shouldRemoveCuteMapperOwnershipFromDomain() throws Exception {
        Path legacyMapperDir = DOMAIN_ROOT.resolve("org/wwz/ai/domain/agent/cute/mapper");
        Path legacySessionMemoryImpl = DOMAIN_ROOT.resolve("org/wwz/ai/domain/agent/cute/service/impl/SessionContextMemoryServiceImpl.java");
        Path legacyWorkspaceImageImpl = DOMAIN_ROOT.resolve("org/wwz/ai/domain/agent/cute/service/impl/WorkspaceImageGenerationServiceImpl.java");
        Assert.assertFalse("domain 不应再保留 cute mapper 目录", Files.exists(legacyMapperDir));
        Assert.assertFalse("domain 不应再保留 SessionContextMemoryServiceImpl 技术实现", Files.exists(legacySessionMemoryImpl));
        Assert.assertFalse("domain 不应再保留 WorkspaceImageGenerationServiceImpl 技术实现", Files.exists(legacyWorkspaceImageImpl));
        assertNoContent(DOMAIN_ROOT, "@Mapper");
        assertNoContent(DOMAIN_ROOT, "BaseMapper<");
        assertNoContent(DOMAIN_ROOT, "org.wwz.ai.domain.agent.cute.mapper");
    }

    @Test
    public void shouldKeepChatModelMetadataInRagContext() {
        Assert.assertEquals("org.wwz.ai.domain.agent.rag.model.chatmodel", ChatModelInfo.class.getPackageName());
        Assert.assertEquals("org.wwz.ai.domain.agent.rag.model.chatmodel", ChatModelSchema.class.getPackageName());
        Assert.assertFalse(Files.exists(DOMAIN_ROOT.resolve("org/wwz/ai/domain/agent/ledger/entity/ChatModelInfo.java")));
        Assert.assertFalse(Files.exists(DOMAIN_ROOT.resolve("org/wwz/ai/domain/agent/ledger/entity/ChatModelSchema.java")));
    }

    @Test
    public void shouldBindMapperXmlToInfrastructureDaoNamespace() throws Exception {
        assertNoContent(MAPPER_ROOT, "org.wwz.ai.domain.agent.cute.mapper.");
        assertContains(MAPPER_ROOT.resolve("dialogue_run_ledger_mapper.xml"), "org.wwz.ai.infrastructure.dao.cute.IDialogueRunLedgerDao");
        assertContains(MAPPER_ROOT.resolve("artifact_ledger_mapper.xml"), "org.wwz.ai.infrastructure.dao.cute.IArtifactLedgerDao");
        assertContains(MAPPER_ROOT.resolve("tool_output_image_generation_mapper.xml"), "org.wwz.ai.infrastructure.dao.cute.IToolOutputImageGenerationDao");
    }

    @Test
    public void shouldKeepMcpTechnicalRuntimeOutOfDomain() throws Exception {
        Path domainMcpRuntime = DOMAIN_ROOT.resolve("org/wwz/ai/domain/agent/runtime/tool/mcp/runtime");
        Assert.assertFalse("domain 不应保留 MCP client runtime 源码", containsJavaSource(domainMcpRuntime));
        assertNoContent(DOMAIN_ROOT, "io.modelcontextprotocol.");
        assertNoContent(DOMAIN_ROOT, "org.springframework.web.reactive.");
    }

    private boolean containsJavaSource(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return false;
        }
        try (var paths = Files.walk(root)) {
            return paths.anyMatch(path -> Files.isRegularFile(path)
                    && path.toString().endsWith(".java"));
        }
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

}
