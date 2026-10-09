package org.wwz.ai.trigger;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Batch 1A 入口边界检查：四个 Controller 不得携带持久化技术类型。
 */
public class CatalogTriggerBoundaryTest {

    private static final Pattern PO_TYPE = Pattern.compile("\\b[A-Za-z][A-Za-z0-9]*PO\\b");
    private static final List<String> CONTROLLERS = List.of(
            "src/main/java/org/wwz/ai/trigger/http/catalog/CatalogController.java",
            "src/main/java/org/wwz/ai/trigger/http/admin/AiClientApiAdminController.java",
            "src/main/java/org/wwz/ai/trigger/http/admin/AiClientModelAdminController.java",
            "src/main/java/org/wwz/ai/trigger/http/admin/AiClientToolMcpAdminController.java"
    );

    @Test
    public void shouldKeepCatalogControllersFreeOfInfrastructureDaoAndPoTypes() throws Exception {
        Path projectRoot = projectRoot();
        for (String relativePath : CONTROLLERS) {
            Path file = projectRoot.resolve("Reactor-agent-trigger").resolve(relativePath);
            String source = Files.readString(file, StandardCharsets.UTF_8);
            Assert.assertFalse(relativePath + " must not import infrastructure",
                    source.contains("org.wwz.ai.infrastructure"));
            Assert.assertFalse(relativePath + " must not import domain types",
                    source.contains("org.wwz.ai.domain"));
            Assert.assertFalse(relativePath + " must not reference DAO", source.contains("Dao"));
            Assert.assertFalse(relativePath + " must not reference PO", PO_TYPE.matcher(source).find());
            Assert.assertFalse(relativePath + " must use Trigger mappers",
                    source.contains("org.wwz.ai.application.catalog.api.ApiConfigMapper")
                            || source.contains("org.wwz.ai.application.catalog.model.ModelConfigMapper")
                            || source.contains("org.wwz.ai.application.catalog.mcp.McpConfigMapper")
                            || source.contains("org.wwz.ai.application.catalog.CatalogCommandMapper")
                            || source.contains("org.wwz.ai.application.catalog.CatalogResponseMapper"));
        }
    }

    @Test
    public void shouldKeepPublicCatalogResponseProjectionFreeOfSecrets() throws Exception {
        Path projectRoot = projectRoot();
        Path mapper = projectRoot.resolve(
                "Reactor-agent-trigger/src/main/java/org/wwz/ai/trigger/http/catalog/mapper/CatalogResponseMapper.java");
        String source = Files.readString(mapper, StandardCharsets.UTF_8);
        Assert.assertFalse(source.contains("apiKey"));
        Assert.assertFalse(source.contains("transportConfig"));
        Assert.assertFalse(source.contains("AiClientToolMcpConfig"));
    }

    private static Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                    && Files.isRegularFile(current.resolve("Reactor-agent-trigger").resolve("pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("无法定位 Reactor-agent 仓库根目录");
    }
}
