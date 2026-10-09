package org.wwz.ai.application.catalog;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Case Catalog 不得把 HTTP/API DTO 或 Response 带入应用层。 */
public class CatalogCaseBoundaryTest {

    @Test
    public void shouldKeepCatalogCaseFreeOfHttpResponseTypes() throws IOException {
        Path root = projectRoot().resolve("Reactor-agent-case/src/main/java/org/wwz/ai/application/catalog");
        try (var files = Files.walk(root)) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    String source = Files.readString(path, StandardCharsets.UTF_8);
                    Assert.assertFalse(path + " must not import API DTOs",
                            source.contains("org.wwz.ai.api.dto"));
                    Assert.assertFalse(path + " must not import HTTP Response",
                            source.contains("org.wwz.ai.api.response.Response"));
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });
        }
    }

    private static Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                    && Files.isDirectory(current.resolve("Reactor-agent-case"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("无法定位 Reactor-agent 仓库根目录");
    }
}
