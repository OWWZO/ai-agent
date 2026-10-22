package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.wwz.ai.domain.agent.adapter.port.FileArtifactPort;
import org.wwz.ai.domain.agent.adapter.port.cli.CliExecutionPort;
import org.wwz.ai.domain.agent.adapter.port.cli.CliInvocation;
import org.wwz.ai.domain.agent.adapter.port.cli.CliResult;
import org.wwz.ai.domain.agent.cute.config.CuteConfig;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.dto.FileRequest;
import org.wwz.ai.domain.agent.runtime.dto.FileResponse;
import org.wwz.ai.domain.agent.runtime.printer.Printer;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.cli.HostCliTool;
import org.wwz.ai.test.domain.support.CuteRuntimeTestSupport;
import org.wwz.ai.types.agent.config.HostCliProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class HostCliToolTest {

    @Test
    public void shouldRejectToolsOutsideAllowlist() {
        HostCliTool tool = new HostCliTool(List.of("git"));
        tool.setAgentContext(contextWith("git"));
        ToolResultPayload payload = (ToolResultPayload) tool.execute(Map.of(
                "tool", "rm",
                "args", List.of("-rf", "/")
        ));
        Assert.assertTrue(Boolean.TRUE.equals(payload.getFailed()));
        Assert.assertEquals("host_cli_denied", payload.getErrorMsg());
    }

    @Test
    public void shouldExecAllowlistedTool() {
        CliExecutionPort cli = Mockito.mock(CliExecutionPort.class);
        Mockito.when(cli.isResolvable("git")).thenReturn(true);
        Mockito.when(cli.exec(Mockito.any())).thenReturn(CliResult.builder()
                .ok(true)
                .tool("git")
                .exitCode(0)
                .stdout("ok")
                .stderr("")
                .durationMs(3)
                .build());
        HostCliTool tool = new HostCliTool(List.of("git"));
        tool.setAgentContext(contextWith(cli));
        ToolResultPayload payload = (ToolResultPayload) tool.execute(Map.of(
                "tool", "git",
                "args", List.of("status")
        ));
        Assert.assertFalse(Boolean.TRUE.equals(payload.getFailed()));
        Assert.assertTrue(String.valueOf(payload.getLlmData()).contains("ok"));
        ArgumentCaptor<CliInvocation> invocationCaptor = ArgumentCaptor.forClass(CliInvocation.class);
        Mockito.verify(cli).exec(invocationCaptor.capture());
        Map<String, String> env = invocationCaptor.getValue().getEnv();
        Assert.assertEquals("http://proxy.test:8080", env.get("HTTP_PROXY"));
        Assert.assertEquals("http://proxy.test:8080", env.get("HTTPS_PROXY"));
        Assert.assertEquals("http://proxy.test:8080", env.get("ALL_PROXY"));
        Assert.assertEquals("http://proxy.test:8080", env.get("http_proxy"));
        Assert.assertEquals("http://proxy.test:8080", env.get("https_proxy"));
        Assert.assertEquals("http://proxy.test:8080", env.get("all_proxy"));
    }

    @Test
    public void shouldRejectWriteCommandsForSocialCli() {
        HostCliTool tool = new HostCliTool(List.of("twitter", "rdt"));
        tool.setAgentContext(contextWith("git"));

        ToolResultPayload twitterPayload = (ToolResultPayload) tool.execute(Map.of(
                "tool", "twitter",
                "args", List.of("post", "hello")
        ));
        ToolResultPayload redditPayload = (ToolResultPayload) tool.execute(Map.of(
                "tool", "rdt",
                "args", List.of("upvote", "abc")
        ));

        Assert.assertTrue(Boolean.TRUE.equals(twitterPayload.getFailed()));
        Assert.assertEquals("host_cli_denied", twitterPayload.getErrorMsg());
        Assert.assertTrue(Boolean.TRUE.equals(redditPayload.getFailed()));
        Assert.assertEquals("host_cli_denied", redditPayload.getErrorMsg());
    }

    @Test
    public void shouldUnsetInheritedProxyWhenWebFetchProxyIsBlank() {
        CliExecutionPort cli = Mockito.mock(CliExecutionPort.class);
        Mockito.when(cli.isResolvable("git")).thenReturn(true);
        Mockito.when(cli.exec(Mockito.any())).thenReturn(CliResult.builder()
                .ok(true)
                .tool("git")
                .exitCode(0)
                .stdout("ok")
                .stderr("")
                .durationMs(3)
                .build());
        HostCliTool tool = new HostCliTool(List.of("git"));
        tool.setAgentContext(contextWith(cli, ""));
        tool.execute(Map.of("tool", "git", "args", List.of("status")));

        ArgumentCaptor<CliInvocation> invocationCaptor = ArgumentCaptor.forClass(CliInvocation.class);
        Mockito.verify(cli).exec(invocationCaptor.capture());
        Assert.assertTrue(invocationCaptor.getValue().getEnv().isEmpty());
        Assert.assertTrue(invocationCaptor.getValue().getUnsetEnv().contains("HTTP_PROXY"));
        Assert.assertTrue(invocationCaptor.getValue().getUnsetEnv().contains("https_proxy"));
    }

    @Test
    public void shouldRegisterFilesProducedByCli() throws IOException {
        Path workspace = Files.createTempDirectory("host-cli-ws");
        try {
            FileArtifactPort fileArtifactPort = fakeFileArtifactPort();
            CliExecutionPort cli = Mockito.mock(CliExecutionPort.class);
            Mockito.when(cli.isResolvable("git")).thenReturn(true);
            Mockito.when(cli.exec(Mockito.any())).thenAnswer(invocation -> {
                Files.write(workspace.resolve("report.csv"), "a,b\n".getBytes(StandardCharsets.UTF_8));
                return CliResult.builder()
                        .ok(true)
                        .tool("git")
                        .exitCode(0)
                        .stdout("ok")
                        .stderr("")
                        .durationMs(3)
                        .artifacts(List.of())
                        .build();
            });
            Printer printer = Mockito.mock(Printer.class);
            HostCliTool tool = new HostCliTool(List.of("git"));
            AgentContext context = artifactContext(cli, fileArtifactPort, printer, workspace);
            tool.setAgentContext(context);

            ToolResultPayload payload = (ToolResultPayload) tool.execute(Map.of(
                    "tool", "git",
                    "args", List.of("status")
            ));

            Assert.assertFalse(Boolean.TRUE.equals(payload.getFailed()));
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) payload.getLlmData();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> artifacts = (List<Map<String, Object>>) data.get("artifacts");
            Assert.assertNotNull(artifacts);
            Assert.assertEquals(1, artifacts.size());
            Assert.assertEquals("report.csv", artifacts.get(0).get("relativePath"));
            Assert.assertEquals("http://fs.test/download/report.csv", artifacts.get(0).get("downloadUrl"));
            Assert.assertEquals("http://fs.test/preview/report.csv", artifacts.get(0).get("previewUrl"));
            // 绝对路径不能出现在工具结果里
            Assert.assertFalse(String.valueOf(data).contains(workspace.toString()));
            Assert.assertEquals(1, context.getArtifactBindingsByToolCallId("call-1").size());
            ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
            Mockito.verify(printer).send(Mockito.eq("file"), eventCaptor.capture(), Mockito.isNull());
            Assert.assertTrue(String.valueOf(eventCaptor.getValue()).contains("report.csv"));
        } finally {
            deleteRecursively(workspace);
        }
    }

    @Test
    public void shouldRegisterFilesWhenCliFails() throws IOException {
        Path workspace = Files.createTempDirectory("host-cli-fail-ws");
        try {
            FileArtifactPort fileArtifactPort = fakeFileArtifactPort();
            CliExecutionPort cli = Mockito.mock(CliExecutionPort.class);
            Mockito.when(cli.isResolvable("git")).thenReturn(true);
            Mockito.when(cli.exec(Mockito.any())).thenAnswer(invocation -> {
                Files.write(workspace.resolve("partial.log"), "x".getBytes(StandardCharsets.UTF_8));
                return CliResult.builder()
                        .ok(false)
                        .tool("git")
                        .exitCode(128)
                        .stdout("")
                        .stderr("fatal: not a repository")
                        .durationMs(3)
                        .artifacts(List.of())
                        .build();
            });
            HostCliTool tool = new HostCliTool(List.of("git"));
            AgentContext context = artifactContext(cli, fileArtifactPort, Mockito.mock(Printer.class), workspace);
            tool.setAgentContext(context);

            ToolResultPayload payload = (ToolResultPayload) tool.execute(Map.of(
                    "tool", "git",
                    "args", List.of("status")
            ));

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) payload.getLlmData();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> artifacts = (List<Map<String, Object>>) data.get("artifacts");
            Assert.assertNotNull(artifacts);
            Assert.assertEquals("partial.log", artifacts.get(0).get("relativePath"));
            Assert.assertEquals(1, context.getArtifactBindingsByToolCallId("call-1").size());
        } finally {
            deleteRecursively(workspace);
        }
    }

    private static FileArtifactPort fakeFileArtifactPort() {
        FileArtifactPort port = Mockito.mock(FileArtifactPort.class);
        try {
            Mockito.when(port.materializeLocalFile(Mockito.anyString(), Mockito.any(FileRequest.class)))
                    .thenAnswer(invocation -> {
                        FileRequest request = invocation.getArgument(1);
                        return FileResponse.builder()
                                .ossUrl("http://fs.test/download/" + request.getFileName())
                                .domainUrl("http://fs.test/preview/" + request.getFileName())
                                .fileSize(4)
                                .fileName(request.getFileName())
                                .build();
                    });
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return port;
    }

    private AgentContext artifactContext(CliExecutionPort cli,
                                         FileArtifactPort fileArtifactPort,
                                         Printer printer,
                                         Path workspace) {
        HostCliProperties properties = new HostCliProperties();
        properties.setEnabled(true);
        properties.setAllow(List.of("git"));
        CuteConfig cuteConfig = CuteRuntimeTestSupport.cuteConfigWithFileService("http://127.0.0.1:1601");
        cuteConfig.setWebFetchProxy("http://proxy.test:8080");
        AgentContext context = AgentContext.builder()
                .requestId("req-1")
                .sessionId("session-1")
                .workspaceRoot(workspace.toString())
                .printer(printer)
                .runtimeDependencies(CuteRuntimeTestSupport.runtimeDependencies(cuteConfig)
                        .toBuilder()
                        .cliExecutionPort(cli)
                        .hostCliProperties(properties)
                        .fileArtifactPort(fileArtifactPort)
                        .build())
                .build();
        context.bindCurrentToolArtifactSource(ToolArtifactSource.builder()
                .sessionId("session-1")
                .requestId("req-1")
                .toolCallId("call-1")
                .toolName("host_cli")
                .build());
        return context;
    }

    private AgentContext contextWith(String ignored) {
        return contextWith(Mockito.mock(CliExecutionPort.class));
    }

    private AgentContext contextWith(CliExecutionPort cli) {
        return contextWith(cli, "http://proxy.test:8080");
    }

    private AgentContext contextWith(CliExecutionPort cli, String webFetchProxy) {
        HostCliProperties properties = new HostCliProperties();
        properties.setEnabled(true);
        properties.setAllow(List.of("git"));
        CuteConfig cuteConfig = new CuteConfig();
        cuteConfig.setWebFetchProxy(webFetchProxy);
        return AgentContext.builder()
                .requestId("req-1")
                .sessionId("session-1")
                .workspaceRoot(tempWorkspace())
                .runtimeDependencies(CuteRuntimeTestSupport.runtimeDependencies(cuteConfig)
                        .toBuilder()
                        .cliExecutionPort(cli)
                        .hostCliProperties(properties)
                        .build())
                .build();
    }

    private static String tempWorkspace() {
        try {
            return Files.createTempDirectory("host-cli-plain-ws").toString();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void deleteRecursively(Path root) {
        try (var walk = Files.walk(root)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignore) {
                    // 临时目录清理失败不影响断言
                }
            });
        } catch (IOException ignore) {
            // ignore
        }
    }
}
