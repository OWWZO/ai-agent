package org.wwz.ai.test.domain;

import com.alibaba.fastjson.JSON;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.wwz.ai.domain.agent.adapter.port.AgentBrowserSession;
import org.wwz.ai.domain.agent.adapter.port.AgentBrowserSessionPort;
import org.wwz.ai.domain.agent.adapter.port.FileArtifactPort;
import org.wwz.ai.domain.agent.adapter.port.cli.CliExecutionPort;
import org.wwz.ai.domain.agent.adapter.port.cli.CliInvocation;
import org.wwz.ai.domain.agent.adapter.port.cli.CliResult;
import org.wwz.ai.domain.agent.runtime.CuteRuntimeDependencies;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.dto.FileRequest;
import org.wwz.ai.domain.agent.runtime.dto.FileResponse;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.ToolObservationSerializer;
import org.wwz.ai.domain.agent.runtime.tool.browser.AgentBrowserTool;
import org.wwz.ai.domain.agent.cute.config.CuteConfig;
import org.wwz.ai.test.domain.support.CuteRuntimeTestSupport;
import org.wwz.ai.types.agent.config.OpenCliProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class AgentBrowserToolTest {

    @Test
    public void shouldExecuteUsingKernelCdpWithoutRelayAndHideEndpointFromPayload() {
        String cdpWsUrl = "wss://proxy.kernel.example/browser/session-secret";
        CliExecutionPort cli = mockSuccessfulCli("connected to " + cdpWsUrl);
        AgentBrowserSessionPort sessionPort = Mockito.mock(AgentBrowserSessionPort.class);
        Mockito.when(sessionPort.isConfigured()).thenReturn(true);
        Mockito.when(sessionPort.resolveForUser("user-1")).thenReturn(AgentBrowserSession.builder()
                .userId("user-1")
                .agentSessionId("session-1")
                .agentBrowserName("rb-user-1")
                .cdpWsUrl(cdpWsUrl)
                .reconstructed(false)
                .build());

        AgentBrowserTool tool = new AgentBrowserTool();
        AgentContext context = context(cli, sessionPort);
        tool.setAgentContext(context);
        ToolResultPayload payload = (ToolResultPayload) tool.execute(Map.of("args", List.of("browser", "list")));

        Assert.assertEquals("agent_browser", tool.getName());
        Assert.assertFalse(Boolean.TRUE.equals(payload.getFailed()));
        Assert.assertNull(context.getRuntimeDependencies().getOptionalBrowserRelayPort());
        ArgumentCaptor<CliInvocation> captor = ArgumentCaptor.forClass(CliInvocation.class);
        Mockito.verify(cli).exec(captor.capture());
        CliInvocation invocation = captor.getValue();
        Assert.assertEquals(cdpWsUrl, invocation.getEnv().get("OPENCLI_CDP_ENDPOINT"));
        Assert.assertFalse(invocation.getEnv().containsKey("OPENCLI_RELAY_URL"));
        Assert.assertFalse(invocation.getEnv().containsKey("OPENCLI_RELAY_SECRET"));
        Assert.assertFalse(invocation.getEnv().containsKey("OPENCLI_RELAY_VISITOR_ID"));
        Assert.assertEquals(List.of("KERNEL_API_KEY"), invocation.getUnsetEnv());
        Assert.assertEquals(List.of("main.js", "browser", "list"), invocation.getArgs());
        Assert.assertFalse(JSON.toJSONString(payload).contains(cdpWsUrl));
    }

    @Test
    public void shouldIncludeNoticeWhenKernelReconstructsBrowser() {
        CliExecutionPort cli = mockSuccessfulCli("{\"ok\":true}");
        AgentBrowserSessionPort sessionPort = Mockito.mock(AgentBrowserSessionPort.class);
        Mockito.when(sessionPort.isConfigured()).thenReturn(true);
        Mockito.when(sessionPort.resolveForUser("user-1")).thenReturn(AgentBrowserSession.builder()
                .userId("user-1")
                .agentSessionId("session-2")
                .agentBrowserName("rb-user-1")
                .cdpWsUrl("wss://proxy.kernel.example/browser/session-2")
                .reconstructed(true)
                .build());

        AgentBrowserTool tool = new AgentBrowserTool();
        tool.setAgentContext(context(cli, sessionPort));
        ToolResultPayload payload = (ToolResultPayload) tool.execute(Map.of("args", List.of("browser", "list")));

        Assert.assertTrue(ToolObservationSerializer.serializePayload(payload)
                .contains("Agent 浏览器已重建，之前的页面状态已丢失。"));
    }

    @Test
    public void shouldRunKernelCallsForSameOwnerConcurrently() throws Exception {
        String cdpWsUrl = "wss://proxy.kernel.example/browser/session-1";
        AgentBrowserSessionPort sessionPort = Mockito.mock(AgentBrowserSessionPort.class);
        Mockito.when(sessionPort.isConfigured()).thenReturn(true);
        Mockito.when(sessionPort.resolveForUser("user-1")).thenReturn(AgentBrowserSession.builder()
                .userId("user-1")
                .agentSessionId("session-1")
                .agentBrowserName("rb-user-1")
                .cdpWsUrl(cdpWsUrl)
                .reconstructed(false)
                .build());

        CliExecutionPort cli = Mockito.mock(CliExecutionPort.class);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch secondEntered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Mockito.doAnswer(invocation -> {
            int current = active.incrementAndGet();
            maxActive.updateAndGet(previous -> Math.max(previous, current));
            if (current == 1) {
                firstEntered.countDown();
            }
            if (current >= 2) {
                secondEntered.countDown();
            }
            try {
                if (!release.await(2, TimeUnit.SECONDS)) {
                    throw new AssertionError("CLI calls were not released");
                }
                return successfulCliResult("{\"ok\":true}");
            } finally {
                active.decrementAndGet();
            }
        }).when(cli).exec(Mockito.any(CliInvocation.class));

        AgentBrowserTool tool = new AgentBrowserTool();
        tool.setAgentContext(context(cli, sessionPort));
        Map<String, Object> input = Map.of("args", List.of("browser", "list"));
        CompletableFuture<Object> first = CompletableFuture.supplyAsync(() -> tool.execute(input));
        CompletableFuture<Object> second = null;
        try {
            Assert.assertTrue(firstEntered.await(1, TimeUnit.SECONDS));
            second = CompletableFuture.supplyAsync(() -> tool.execute(input));
            Assert.assertTrue(secondEntered.await(1, TimeUnit.SECONDS));
        } finally {
            release.countDown();
        }

        Assert.assertNotNull(second);
        ToolResultPayload firstPayload = (ToolResultPayload) first.get(1, TimeUnit.SECONDS);
        ToolResultPayload secondPayload = (ToolResultPayload) second.get(1, TimeUnit.SECONDS);
        Assert.assertFalse(Boolean.TRUE.equals(firstPayload.getFailed()));
        Assert.assertFalse(Boolean.TRUE.equals(secondPayload.getFailed()));
        Assert.assertEquals(2, maxActive.get());
        Mockito.verify(cli, Mockito.times(2)).exec(Mockito.any(CliInvocation.class));
    }

    @Test
    public void shouldRegisterDownloadedFilesWithoutExposingCdpUrl() throws Exception {
        String cdpWsUrl = "wss://proxy.kernel.example/browser/session-secret";
        Path workspace = Files.createTempDirectory("agent-browser-ws");
        try {
            CliExecutionPort cli = Mockito.mock(CliExecutionPort.class);
            Mockito.when(cli.exec(Mockito.any())).thenAnswer(invocation -> {
                Path target = workspace.resolve("xhs").resolve("note-1_1.jpg");
                Files.createDirectories(target.getParent());
                Files.write(target, "image".getBytes(StandardCharsets.UTF_8));
                return successfulCliResult("{\"ok\":true}");
            });
            AgentBrowserTool tool = new AgentBrowserTool();
            AgentContext context = context(cli, sessionPort(cdpWsUrl, false), workspace,
                    artifactPort(), "call-1");
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
            Assert.assertEquals(1, context.getArtifactBindingsByToolCallId("call-1").size());
            // CDP URL 不能出现在工具结果或 artifact 元数据里
            Assert.assertFalse(JSON.toJSONString(payload).contains(cdpWsUrl));
            Assert.assertFalse(String.valueOf(artifacts).contains(cdpWsUrl));
        } finally {
            deleteRecursively(workspace);
        }
    }

    @Test
    public void shouldNotCrossBindConcurrentDownloadArtifacts() throws Exception {
        String cdpWsUrl = "wss://proxy.kernel.example/browser/session-1";
        Path workspace = Files.createTempDirectory("agent-browser-concurrent-ws");
        try {
            CliExecutionPort cli = Mockito.mock(CliExecutionPort.class);
            AtomicInteger active = new AtomicInteger();
            CountDownLatch bothEntered = new CountDownLatch(2);
            CountDownLatch release = new CountDownLatch(1);
            Mockito.doAnswer(invocation -> {
                CliInvocation cliInvocation = invocation.getArgument(0);
                List<String> args = cliInvocation.getArgs();
                String outputRoot = args.get(args.indexOf("--output") + 1);
                active.incrementAndGet();
                bothEntered.countDown();
                try {
                    if (!release.await(2, TimeUnit.SECONDS)) {
                        throw new AssertionError("CLI calls were not released");
                    }
                    Path target = workspace.resolve(outputRoot).resolve("note.jpg");
                    Files.createDirectories(target.getParent());
                    Files.write(target, outputRoot.getBytes(StandardCharsets.UTF_8));
                    return successfulCliResult("{\"ok\":true}");
                } finally {
                    active.decrementAndGet();
                }
            }).when(cli).exec(Mockito.any(CliInvocation.class));

            AgentBrowserSessionPort sessionPort = sessionPort(cdpWsUrl, false);
            FileArtifactPort port = artifactPort();
            AgentContext first = context(cli, sessionPort, workspace, port, "call-1");
            AgentContext second = context(cli, sessionPort, workspace, port, "call-2");
            AgentBrowserTool firstTool = new AgentBrowserTool();
            firstTool.setAgentContext(first);
            AgentBrowserTool secondTool = new AgentBrowserTool();
            secondTool.setAgentContext(second);

            Map<String, Object> firstInput = Map.of("args",
                    List.of("xiaohongshu", "download", "https://x.test/a", "--output", "./out-a"));
            Map<String, Object> secondInput = Map.of("args",
                    List.of("xiaohongshu", "download", "https://x.test/b", "--output", "./out-b"));
            CompletableFuture<Object> firstCall = new CompletableFuture<>();
            CompletableFuture<Object> secondCall = new CompletableFuture<>();
            // ToolArtifactSource 是 ThreadLocal：必须在工作线程内绑定，否则产物不会挂到 toolCallId
            Thread firstThread = new Thread(() -> {
                first.bindCurrentToolArtifactSource(artifactSource("call-1"));
                firstCall.complete(firstTool.execute(firstInput));
            });
            Thread secondThread = new Thread(() -> {
                second.bindCurrentToolArtifactSource(artifactSource("call-2"));
                secondCall.complete(secondTool.execute(secondInput));
            });
            firstThread.start();
            secondThread.start();
            try {
                Assert.assertTrue(bothEntered.await(2, TimeUnit.SECONDS));
            } finally {
                release.countDown();
            }
            firstThread.join(2_000);
            secondThread.join(2_000);

            Assert.assertEquals(1, first.getArtifactBindingsByToolCallId("call-1").size());
            Assert.assertEquals(1, second.getArtifactBindingsByToolCallId("call-2").size());
            Assert.assertEquals("out-a/note.jpg",
                    first.getArtifactBindingsByToolCallId("call-1").get(0).getFile().getRelativePath());
            Assert.assertEquals("out-b/note.jpg",
                    second.getArtifactBindingsByToolCallId("call-2").get(0).getFile().getRelativePath());
        } finally {
            deleteRecursively(workspace);
        }
    }

    @Test
    public void shouldInjectDedicatedOutputRootForDownloadWithoutOutput() throws Exception {
        Path workspace = Files.createTempDirectory("agent-browser-injected-ws");
        try {
            CliExecutionPort cli = Mockito.mock(CliExecutionPort.class);
            Mockito.when(cli.exec(Mockito.any())).thenAnswer(invocation -> {
                CliInvocation command = invocation.getArgument(0);
                String output = command.getArgs().get(command.getArgs().indexOf("--output") + 1);
                Path target = workspace.resolve(output).resolve("note.jpg");
                Files.createDirectories(target.getParent());
                Files.write(target, "image".getBytes(StandardCharsets.UTF_8));
                return successfulCliResult("{}");
            });
            AgentBrowserTool tool = new AgentBrowserTool();
            AgentContext context = context(cli,
                    sessionPort("wss://proxy.kernel.example/browser/injected", false),
                    workspace, artifactPort(), "call-1");
            tool.setAgentContext(context);

            ToolResultPayload payload = (ToolResultPayload) tool.execute(Map.of(
                    "args", List.of("xiaohongshu", "download", "<signed-note-url>")));

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) payload.getLlmData();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> artifacts = (List<Map<String, Object>>) data.get("artifacts");
            Assert.assertEquals(1, artifacts.size());
            Assert.assertTrue(String.valueOf(artifacts.get(0).get("relativePath"))
                    .matches("\\.reactor-artifacts/agent_browser/[A-Za-z0-9_-]+/note.jpg"));
            Assert.assertFalse(JSON.toJSONString(payload).contains(workspace.toString()));
        } finally {
            deleteRecursively(workspace);
        }
    }

    private static ToolArtifactSource artifactSource(String toolCallId) {
        return ToolArtifactSource.builder()
                .sessionId("session-1")
                .requestId("req-" + toolCallId)
                .toolCallId(toolCallId)
                .toolName("agent_browser")
                .build();
    }

    private static AgentBrowserSessionPort sessionPort(String cdpWsUrl, boolean reconstructed) {
        AgentBrowserSessionPort sessionPort = Mockito.mock(AgentBrowserSessionPort.class);
        Mockito.when(sessionPort.isConfigured()).thenReturn(true);
        Mockito.when(sessionPort.resolveForUser("user-1")).thenReturn(AgentBrowserSession.builder()
                .userId("user-1")
                .agentSessionId("session-1")
                .agentBrowserName("rb-user-1")
                .cdpWsUrl(cdpWsUrl)
                .reconstructed(reconstructed)
                .build());
        return sessionPort;
    }

    private static FileArtifactPort artifactPort() {
        FileArtifactPort port = Mockito.mock(FileArtifactPort.class);
        try {
            Mockito.when(port.materializeLocalFile(Mockito.anyString(), Mockito.any(FileRequest.class)))
                    .thenAnswer(invocation -> {
                        FileRequest request = invocation.getArgument(1);
                        return FileResponse.builder()
                                .ossUrl("http://fs.test/download/" + request.getFileName())
                                .domainUrl("http://fs.test/preview/" + request.getFileName())
                                .fileSize(5)
                                .fileName(request.getFileName())
                                .build();
                    });
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return port;
    }

    private static AgentContext context(CliExecutionPort cli,
                                        AgentBrowserSessionPort sessionPort,
                                        Path workspace,
                                        FileArtifactPort fileArtifactPort,
                                        String toolCallId) {
        OpenCliProperties properties = new OpenCliProperties();
        properties.setEnabled(true);
        properties.setCommand("node");
        properties.setPrefixArgs(List.of("main.js"));
        properties.setCacheDir("kernel_cache");
        CuteConfig cuteConfig = CuteRuntimeTestSupport.cuteConfigWithFileService("http://127.0.0.1:1601");
        CuteRuntimeDependencies dependencies = CuteRuntimeTestSupport.runtimeDependencies(cuteConfig)
                .toBuilder()
                .cliExecutionPort(cli)
                .openCliProperties(properties)
                .agentBrowserSessionPort(sessionPort)
                .fileArtifactPort(fileArtifactPort)
                .build();
        AgentContext context = AgentContext.builder()
                .requestId("req-" + toolCallId)
                .sessionId("session-1")
                .userId("user-1")
                .workspaceRoot(workspace.toString())
                .runtimeDependencies(dependencies)
                .build();
        context.bindCurrentToolArtifactSource(ToolArtifactSource.builder()
                .sessionId("session-1")
                .requestId("req-" + toolCallId)
                .toolCallId(toolCallId)
                .toolName("agent_browser")
                .build());
        return context;
    }

    private static void deleteRecursively(Path root) {
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
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

    private static CliExecutionPort mockSuccessfulCli(String stdout) {
        CliExecutionPort cli = Mockito.mock(CliExecutionPort.class);
        Mockito.when(cli.exec(Mockito.any())).thenReturn(successfulCliResult(stdout));
        return cli;
    }

    private static CliResult successfulCliResult(String stdout) {
        return CliResult.builder()
                .ok(true)
                .tool("opencli")
                .exitCode(0)
                .stdout(stdout)
                .stderr("")
                .durationMs(12)
                .timedOut(false)
                .truncated(false)
                .artifacts(List.of())
                .build();
    }

    private static AgentContext context(CliExecutionPort cli, AgentBrowserSessionPort sessionPort) {
        OpenCliProperties properties = new OpenCliProperties();
        properties.setEnabled(true);
        properties.setCommand("node");
        properties.setPrefixArgs(List.of("main.js"));
        properties.setCacheDir("kernel_cache");
        CuteRuntimeDependencies dependencies = CuteRuntimeTestSupport.runtimeDependencies(new CuteConfig())
                .toBuilder()
                .cliExecutionPort(cli)
                .openCliProperties(properties)
                .agentBrowserSessionPort(sessionPort)
                .build();
        return AgentContext.builder()
                .requestId("req-1")
                .sessionId("session-1")
                .userId("user-1")
                .workspaceRoot(System.getProperty("java.io.tmpdir"))
                .runtimeDependencies(dependencies)
                .build();
    }
}
