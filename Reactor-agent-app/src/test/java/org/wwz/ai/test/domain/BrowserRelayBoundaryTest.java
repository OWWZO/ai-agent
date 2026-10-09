package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BrowserRelayBoundaryTest {

    private static final Path PROJECT_ROOT = BoundaryTestPaths.projectRoot();
    private static final Path DOMAIN_BROWSER = PROJECT_ROOT.resolve(
            "Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/browser");
    private static final Path SOCKET_PORT = PROJECT_ROOT.resolve(
            "Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/adapter/port/BrowserRelaySocketPort.java");
    private static final Path CASE_BROWSER = PROJECT_ROOT.resolve(
            "Reactor-agent-case/src/main/java/org/wwz/ai/application/agent/browser");
    private static final Path TRIGGER_BROWSER = PROJECT_ROOT.resolve(
            "Reactor-agent-trigger/src/main/java/org/wwz/ai/trigger/http/browser");

    @Test
    public void shouldKeepBrowserRelayDomainAndCaseFreeOfJsonAndWebSocketTypes() throws IOException {
        List<Path> sources = javaSources(DOMAIN_BROWSER);
        sources.add(SOCKET_PORT);
        sources.addAll(javaSources(CASE_BROWSER));
        List<String> offenders = sources.stream()
                .filter(this::containsProtocolType)
                .map(path -> PROJECT_ROOT.relativize(path).toString().replace('\\', '/'))
                .sorted()
                .collect(Collectors.toList());
        Assert.assertTrue("Browser Relay domain/case must not import JSON or WebSocket types: " + offenders,
                offenders.isEmpty());
    }

    @Test
    public void shouldRemoveRawSocketLifecycleMethodsFromDomainPort() throws IOException {
        String source = Files.readString(SOCKET_PORT, StandardCharsets.UTF_8);
        Assert.assertFalse(source.contains("onText("));
        Assert.assertFalse(source.contains("send(String"));
        Assert.assertFalse(source.contains("close()"));
        Assert.assertFalse(source.contains("interface Connection"));
    }

    @Test
    public void shouldKeepWebSocketAdaptationInTriggerAndUseApplicationServiceSeam() throws IOException {
        String hub = Files.readString(TRIGGER_BROWSER.resolve("BrowserRelayHub.java"), StandardCharsets.UTF_8);
        Assert.assertTrue(hub.contains("BrowserRelayApplicationService"));
        Assert.assertFalse(hub.contains("BrowserRelaySocketPort"));
        Assert.assertTrue(javaSources(TRIGGER_BROWSER).stream().anyMatch(this::containsWebSocketType));
    }

    private List<Path> javaSources(Path root) throws IOException {
        if (Files.isRegularFile(root)) {
            return new ArrayList<>(List.of(root));
        }
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toCollection(ArrayList::new));
        }
    }

    private boolean containsProtocolType(Path path) {
        try {
            String source = Files.readString(path, StandardCharsets.UTF_8);
            return source.contains("org.springframework.web.socket")
                    || source.contains("com.alibaba.fastjson");
        } catch (IOException exception) {
            throw new IllegalStateException("读取文件失败: " + path, exception);
        }
    }

    private boolean containsWebSocketType(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8)
                    .contains("org.springframework.web.socket");
        } catch (IOException exception) {
            throw new IllegalStateException("读取文件失败: " + path, exception);
        }
    }
}
