package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.wwz.ai.domain.agent.adapter.port.BrowserRelayPort;
import org.wwz.ai.domain.agent.adapter.port.FileArtifactPort;
import org.wwz.ai.domain.agent.adapter.port.cli.CliExecutionPort;
import org.wwz.ai.domain.agent.adapter.port.cli.CliInvocation;
import org.wwz.ai.domain.agent.adapter.port.cli.CliResult;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.dto.FileRequest;
import org.wwz.ai.domain.agent.runtime.dto.FileResponse;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.cli.OpenCliArgv;
import org.wwz.ai.domain.agent.runtime.tool.browser.BrowserTool;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.test.domain.support.ReactorRuntimeTestSupport;
import org.wwz.ai.types.agent.config.OpenCliProperties;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class BrowserToolTest {

    @Test
    public void shouldExposeBrowserToolWithCliUsageGuidance() {
        BrowserTool tool = new BrowserTool();

        Assert.assertEquals("browser", tool.getName());
        Assert.assertEquals("远程操作用户的浏览器。", tool.getDescription());
        Map<?, ?> params = (Map<?, ?>) tool.toParams().get("properties");
        Map<?, ?> args = (Map<?, ?>) params.get("args");
        String argsDescription = String.valueOf(args.get("description"));
        Assert.assertTrue(argsDescription.contains("list"));
        Assert.assertTrue(argsDescription.contains("--help"));
    }

    @Test
    public void shouldPreserveBrowserArgsAndAppendFormatOnlyForSiteCommands() {
        Assert.assertEquals(
                List.of("browser", "click", "12"),
                OpenCliArgv.rewrite(List.of("browser", "click", "12"), List.of("-f", "json")));
        Assert.assertEquals(
                List.of("browser", "visitor:vis_zhangsan", "click", "12"),
                OpenCliArgv.rewrite(List.of("browser", "visitor:vis_zhangsan", "click", "12"),
                        List.of("-f", "json")));
        Assert.assertEquals(
                List.of("bilibili", "search", "redis", "-f", "json"),
                OpenCliArgv.rewrite(List.of("bilibili", "search", "redis"), List.of("-f", "json")));
        Assert.assertEquals(
                List.of("xiaohongshu", "feed", "--limit", "10", "-f", "json"),
                OpenCliArgv.rewrite(List.of("xiaohongshu", "feed", "--limit", "10", "-f", "json"),
                        List.of("-f", "json")));
        Assert.assertEquals(
                List.of("bilibili", "search", "redis", "--keep-tab", "false", "-f", "json"),
                OpenCliArgv.rewrite(List.of("bilibili", "search", "redis", "--keep-tab", "false"),
                        List.of("-f", "json")));
        Assert.assertEquals(
                List.of("list", "-f", "json"),
                OpenCliArgv.rewrite(List.of("list"), List.of("-f", "json")));
    }

    @Test
    public void shouldKeepCookiesInObservationAndRedactLedger() {
        CliExecutionPort cli = Mockito.mock(CliExecutionPort.class);
        Mockito.when(cli.exec(Mockito.any())).thenReturn(CliResult.builder()
                .ok(true)
                .tool("opencli")
                .exitCode(0)
                .stdout("{\"cookies\":[{\"name\":\"sid\",\"value\":\"secret-cookie\"}]}")
                .stderr("")
                .durationMs(12)
                .timedOut(false)
                .truncated(false)
                .artifacts(List.of())
                .build());
        BrowserTool tool = new BrowserTool();
        tool.setAgentContext(onlineContext(cli));

        ToolResultPayload payload = (ToolResultPayload) tool.execute(Map.of("args", List.of("browser", "cookies")));

        Assert.assertFalse(Boolean.TRUE.equals(payload.getFailed()));
        // 敏感命令：模型需要完整输出才能继续推理，落库/回放/前端只保留脱敏版。
        Assert.assertTrue(payload.getLlmObservation().contains("secret-cookie"));
        Assert.assertFalse(payload.getToolResult().contains("secret-cookie"));
        Assert.assertTrue(payload.getToolResult().contains("redacted"));
        ArgumentCaptor<CliInvocation> captor = ArgumentCaptor.forClass(CliInvocation.class);
        Mockito.verify(cli).exec(captor.capture());
        Assert.assertEquals(List.of("main.js", "browser", "cookies"), captor.getValue().getArgs());
        Assert.assertTrue(captor.getValue().getEnv().get("OPENCLI_CACHE_DIR").replace('\\', '/').endsWith("vis_cache/user-1"));
        Assert.assertEquals("http://127.0.0.1:8100/internal/browser/rpc", captor.getValue().getEnv().get("OPENCLI_RELAY_URL"));
        Assert.assertFalse(captor.getValue().getEnv().containsKey("OPENCLI_SITE_SESSION"));
    }

    @Test
    public void shouldRegisterDownloadedFilesAndKeepRelayEnvironment() throws Exception {
        Path workspace = Files.createTempDirectory("browser-ws");
        try {
            CliExecutionPort cli = Mockito.mock(CliExecutionPort.class);
            Mockito.when(cli.exec(Mockito.any())).thenAnswer(invocation -> {
                Path target = workspace.resolve("xhs").resolve("note-1_1.jpg");
                Files.createDirectories(target.getParent());
                Files.write(target, "image".getBytes(StandardCharsets.UTF_8));
                return CliResult.builder()
                        .ok(true)
                        .tool("opencli")
                        .exitCode(0)
                        .stdout("{\"status\":\"ok\"}")
                        .stderr("")
                        .durationMs(12)
                        .artifacts(List.of())
                        .build();
            });
            BrowserTool tool = new BrowserTool();
            AgentContext context = onlineArtifactContext(cli, workspace);
            tool.setAgentContext(context);

            ToolResultPayload payload = (ToolResultPayload) tool.execute(Map.of("args",
                    List.of("xiaohongshu", "download", "https://x.test/note", "--output", "./xhs")));

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) payload.getLlmData();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> artifacts = (List<Map<String, Object>>) data.get("artifacts");
            Assert.assertNotNull(artifacts);
            Assert.assertEquals(1, artifacts.size());
            Assert.assertEquals("xhs/note-1_1.jpg", artifacts.get(0).get("relativePath"));
            Assert.assertEquals("http://fs.test/download/xhs/note-1_1.jpg", artifacts.get(0).get("downloadUrl"));
            Assert.assertFalse(String.valueOf(data).contains(workspace.toString()));
            Assert.assertEquals(1, context.getArtifactBindingsByToolCallId("call-1").size());

            ArgumentCaptor<CliInvocation> captor = ArgumentCaptor.forClass(CliInvocation.class);
            Mockito.verify(cli).exec(captor.capture());
            Assert.assertEquals("http://127.0.0.1:8100/internal/browser/rpc",
                    captor.getValue().getEnv().get("OPENCLI_RELAY_URL"));
            Assert.assertEquals("user-1", captor.getValue().getEnv().get("OPENCLI_RELAY_VISITOR_ID"));
            Assert.assertEquals(workspace.toString(), captor.getValue().getCwd());
            // relay 地址、visitor id、缓存目录都不能进入产物元数据
            String artifactJson = String.valueOf(artifacts);
            Assert.assertFalse(artifactJson.contains("internal/browser/rpc"));
            Assert.assertFalse(artifactJson.contains("vis_cache"));
        } finally {
            try (var walk = Files.walk(workspace)) {
                walk.sorted(java.util.Comparator.reverseOrder())
                        .forEach(path -> path.toFile().delete());
            }
        }
    }

    @Test
    public void shouldIgnoreWorkspaceFilesWhenNoOutputRootDeclared() throws Exception {
        Path workspace = Files.createTempDirectory("browser-ws2");
        try {
            Files.write(workspace.resolve("stray.txt"),
                    "x".getBytes(StandardCharsets.UTF_8));
            CliExecutionPort cli = Mockito.mock(CliExecutionPort.class);
            Mockito.when(cli.exec(Mockito.any())).thenReturn(CliResult.builder()
                    .ok(true)
                    .tool("opencli")
                    .exitCode(0)
                    .stdout("{\"status\":\"ok\"}")
                    .stderr("")
                    .durationMs(4)
                    .artifacts(List.of())
                    .build());
            BrowserTool tool = new BrowserTool();
            AgentContext context = onlineArtifactContext(cli, workspace);
            tool.setAgentContext(context);

            ToolResultPayload payload = (ToolResultPayload) tool.execute(Map.of("args",
                    List.of("browser", "click", "12")));

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) payload.getLlmData();
            Assert.assertFalse(data.containsKey("artifacts"));
            Assert.assertTrue(context.getVisibleArtifactBindings().isEmpty());
        } finally {
            try (var walk = Files.walk(workspace)) {
                walk.sorted(java.util.Comparator.reverseOrder())
                        .forEach(path -> path.toFile().delete());
            }
        }
    }

    @Test
    public void shouldInjectDedicatedOutputRootForDownloadWithoutOutput() throws Exception {
        Path workspace = Files.createTempDirectory("browser-injected-ws");
        try {
            CliExecutionPort cli = Mockito.mock(CliExecutionPort.class);
            Mockito.when(cli.exec(Mockito.any())).thenAnswer(invocation -> {
                CliInvocation command = invocation.getArgument(0);
                List<String> args = command.getArgs();
                String output = args.get(args.indexOf("--output") + 1);
                Path target = workspace.resolve(output).resolve("note.jpg");
                Files.createDirectories(target.getParent());
                Files.write(target, "image".getBytes(StandardCharsets.UTF_8));
                return CliResult.builder()
                        .ok(true)
                        .tool("opencli")
                        .exitCode(0)
                        .stdout("{}")
                        .stderr("")
                        .durationMs(4)
                        .artifacts(List.of())
                        .build();
            });
            BrowserTool tool = new BrowserTool();
            AgentContext context = onlineArtifactContext(cli, workspace);
            tool.setAgentContext(context);

            ToolResultPayload payload = (ToolResultPayload) tool.execute(Map.of(
                    "args", List.of("xiaohongshu", "download", "<signed-note-url>")));

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) payload.getLlmData();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> artifacts = (List<Map<String, Object>>) data.get("artifacts");
            Assert.assertEquals(1, artifacts.size());
            Assert.assertTrue(String.valueOf(artifacts.get(0).get("relativePath"))
                    .matches("\\.reactor-artifacts/browser/[A-Za-z0-9_-]+/note.jpg"));
            Assert.assertFalse(String.valueOf(data).contains(workspace.toString()));
            ArgumentCaptor<CliInvocation> captor = ArgumentCaptor.forClass(CliInvocation.class);
            Mockito.verify(cli).exec(captor.capture());
            Assert.assertEquals("--output", captor.getValue().getArgs().get(captor.getValue().getArgs().size() - 2));
        } finally {
            try (var walk = Files.walk(workspace)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
            }
        }
    }

    @Test
    public void shouldMaterializePartialFileWhenCliThrows() throws Exception {
        Path workspace = Files.createTempDirectory("browser-exception-ws");
        try {
            CliExecutionPort cli = Mockito.mock(CliExecutionPort.class);
            Mockito.when(cli.exec(Mockito.any())).thenAnswer(invocation -> {
                CliInvocation command = invocation.getArgument(0);
                String output = command.getArgs().get(command.getArgs().indexOf("--output") + 1);
                Path target = workspace.resolve(output).resolve("partial.jpg");
                Files.createDirectories(target.getParent());
                Files.write(target, "partial".getBytes(StandardCharsets.UTF_8));
                throw new IllegalStateException("cli process broke");
            });
            BrowserTool tool = new BrowserTool();
            AgentContext context = onlineArtifactContext(cli, workspace);
            tool.setAgentContext(context);

            ToolResultPayload payload = (ToolResultPayload) tool.execute(Map.of(
                    "args", List.of("xiaohongshu", "download", "<signed-note-url>")));

            Assert.assertTrue(Boolean.TRUE.equals(payload.getFailed()));
            Assert.assertEquals("opencli_execution_failed", payload.getErrorMsg());
            Assert.assertTrue(String.valueOf(payload.getLlmData()).contains("partial.jpg"));
            Assert.assertEquals(1, context.getArtifactBindingsByToolCallId("call-1").size());
        } finally {
            try (var walk = Files.walk(workspace)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
            }
        }
    }

    private AgentContext onlineArtifactContext(CliExecutionPort cli, Path workspace) {
        BrowserRelayPort relay = Mockito.mock(BrowserRelayPort.class);
        Mockito.when(relay.isOnline("user-1")).thenReturn(true);
        OpenCliProperties properties = new OpenCliProperties();
        properties.setEnabled(true);
        properties.setCommand("node");
        properties.setPrefixArgs(List.of("main.js"));
        properties.setCacheDir("vis_cache");
        FileArtifactPort port =
                Mockito.mock(FileArtifactPort.class);
        try {
            Mockito.when(port.materializeLocalFile(Mockito.anyString(),
                    Mockito.any(FileRequest.class)))
                    .thenAnswer(invocation -> {
                        FileRequest request = invocation.getArgument(1);
                        return FileResponse.builder()
                                .ossUrl("http://fs.test/download/" + request.getFileName())
                                .domainUrl("http://fs.test/preview/" + request.getFileName())
                                .fileSize(5)
                                .fileName(request.getFileName())
                                .build();
                    });
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        ReactorConfig reactorConfig = ReactorRuntimeTestSupport.reactorConfigWithFileService("http://127.0.0.1:1601");
        AgentContext context = AgentContext.builder()
                .requestId("req-1")
                .sessionId("session-1")
                .userId("user-1")
                .workspaceRoot(workspace.toString())
                .runtimeDependencies(ReactorRuntimeTestSupport.runtimeDependencies(reactorConfig)
                        .toBuilder()
                        .browserRelayPort(relay)
                        .cliExecutionPort(cli)
                        .openCliProperties(properties)
                        .fileArtifactPort(port)
                        .build())
                .build();
        context.bindCurrentToolArtifactSource(ToolArtifactSource.builder()
                .sessionId("session-1")
                .requestId("req-1")
                .toolCallId("call-1")
                .toolName("browser")
                .build());
        return context;
    }

    private AgentContext onlineContext(CliExecutionPort cli) {
        BrowserRelayPort relay = Mockito.mock(BrowserRelayPort.class);
        Mockito.when(relay.isOnline("user-1")).thenReturn(true);
        OpenCliProperties properties = new OpenCliProperties();
        properties.setEnabled(true);
        properties.setCommand("node");
        properties.setPrefixArgs(List.of("main.js"));
        properties.setCacheDir("vis_cache");
        AgentContext ctx = AgentContext.builder()
                .requestId("req-1")
                .sessionId("session-1")
                .userId("user-1")
                .workspaceRoot(System.getProperty("java.io.tmpdir"))
                .runtimeDependencies(ReactorRuntimeTestSupport.runtimeDependencies(new ReactorConfig())
                        .toBuilder()
                        .browserRelayPort(relay)
                        .cliExecutionPort(cli)
                        .openCliProperties(properties)
                        .build())
                .build();
        return ctx;
    }
}
