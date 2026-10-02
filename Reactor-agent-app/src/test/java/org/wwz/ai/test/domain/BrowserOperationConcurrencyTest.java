package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.wwz.ai.application.agent.kernelbrowser.KernelBrowserApplicationService;
import org.wwz.ai.domain.agent.adapter.port.BrowserOperationKind;
import org.wwz.ai.domain.agent.adapter.port.BrowserOperationLockPort;
import org.wwz.ai.domain.agent.adapter.port.BrowserOperationLockTimeoutException;
import org.wwz.ai.domain.agent.adapter.port.BrowserRelayPort;
import org.wwz.ai.domain.agent.adapter.port.KernelBrowserLiveView;
import org.wwz.ai.domain.agent.adapter.port.KernelBrowserSession;
import org.wwz.ai.domain.agent.adapter.port.KernelBrowserSessionPort;
import org.wwz.ai.domain.agent.adapter.port.KernelBrowserSessionStatus;
import org.wwz.ai.domain.agent.adapter.port.cli.CliExecutionPort;
import org.wwz.ai.domain.agent.adapter.port.cli.CliInvocation;
import org.wwz.ai.domain.agent.adapter.port.cli.CliResult;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.browser.BrowserTool;
import org.wwz.ai.domain.agent.runtime.tool.browser.KernelBrowserTool;
import org.wwz.ai.domain.agent.runtime.tool.cli.HostCliTool;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.infrastructure.adapter.port.InProcessBrowserOperationLockAdapter;
import org.wwz.ai.test.domain.support.ReactorRuntimeTestSupport;
import org.wwz.ai.types.agent.config.HostCliProperties;
import org.wwz.ai.types.agent.config.OpenCliProperties;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class BrowserOperationConcurrencyTest {

    @Test
    public void shouldSerializeBrowserCallsForSameUser() throws Exception {
        InProcessBrowserOperationLockAdapter lock = new InProcessBrowserOperationLockAdapter();
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch secondEntered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger maxActive = new AtomicInteger();
        AtomicInteger active = new AtomicInteger();
        CliExecutionPort cli = Mockito.mock(CliExecutionPort.class);
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
                    throw new AssertionError("CLI was not released");
                }
                return success();
            } finally {
                active.decrementAndGet();
            }
        }).when(cli).exec(Mockito.any(CliInvocation.class));
        BrowserTool tool = browserTool("visitor-1", cli, onlineRelay("visitor-1"), lock);
        CompletableFuture<Object> first = CompletableFuture.supplyAsync(() -> tool.execute(command()));
        CompletableFuture<Object> second;
        try {
            Assert.assertTrue(firstEntered.await(1, TimeUnit.SECONDS));
            second = CompletableFuture.supplyAsync(() -> tool.execute(command()));
            Assert.assertFalse(secondEntered.await(200, TimeUnit.MILLISECONDS));
        } finally {
            release.countDown();
        }
        Assert.assertFalse(failed(first.get(1, TimeUnit.SECONDS)));
        Assert.assertFalse(failed(second.get(1, TimeUnit.SECONDS)));
        Assert.assertEquals(1, maxActive.get());
    }

    @Test
    public void shouldRunBrowserAndKernelConcurrentlyForSameUser() throws Exception {
        InProcessBrowserOperationLockAdapter lock = new InProcessBrowserOperationLockAdapter();
        CliGate gate = new CliGate();
        BrowserTool browser = browserTool("visitor-1", gate.cli, onlineRelay("visitor-1"), lock);
        KernelBrowserTool kernel = kernelTool("visitor-1", gate.cli, kernelPort("visitor-1"), lock);
        assertOverlapped(gate, () -> browser.execute(command()), () -> kernel.execute(command()));
    }

    @Test
    public void shouldRunSameBrowserKindConcurrentlyForDifferentUsers() throws Exception {
        InProcessBrowserOperationLockAdapter lock = new InProcessBrowserOperationLockAdapter();
        CliGate gate = new CliGate();
        BrowserRelayPort relay = Mockito.mock(BrowserRelayPort.class);
        Mockito.when(relay.isOnline("visitor-1")).thenReturn(true);
        Mockito.when(relay.isOnline("visitor-2")).thenReturn(true);
        BrowserTool first = browserTool("visitor-1", gate.cli, relay, lock);
        BrowserTool second = browserTool("visitor-2", gate.cli, relay, lock);
        assertOverlapped(gate, () -> first.execute(command()), () -> second.execute(command()));

        CliGate kernelGate = new CliGate();
        KernelBrowserSessionPort sessionPort = Mockito.mock(KernelBrowserSessionPort.class);
        Mockito.when(sessionPort.isConfigured()).thenReturn(true);
        Mockito.when(sessionPort.resolveForOwner("visitor-1")).thenReturn(session("visitor-1"));
        Mockito.when(sessionPort.resolveForOwner("visitor-2")).thenReturn(session("visitor-2"));
        KernelBrowserTool kernelFirst = kernelTool("visitor-1", kernelGate.cli, sessionPort, lock);
        KernelBrowserTool kernelSecond = kernelTool("visitor-2", kernelGate.cli, sessionPort, lock);
        assertOverlapped(kernelGate, () -> kernelFirst.execute(command()), () -> kernelSecond.execute(command()));
    }

    @Test
    public void shouldKeepKernelEnsureAndResetExclusiveWithKernelTool() throws Exception {
        InProcessBrowserOperationLockAdapter lock = new InProcessBrowserOperationLockAdapter();
        AtomicReference<CountDownLatch> cliEntered = new AtomicReference<>(new CountDownLatch(1));
        AtomicReference<CountDownLatch> release = new AtomicReference<>(new CountDownLatch(1));
        CliExecutionPort cli = Mockito.mock(CliExecutionPort.class);
        Mockito.doAnswer(invocation -> {
            cliEntered.get().countDown();
            if (!release.get().await(2, TimeUnit.SECONDS)) {
                throw new AssertionError("CLI was not released");
            }
            return success();
        }).when(cli).exec(Mockito.any(CliInvocation.class));
        KernelBrowserSessionPort sessionPort = kernelPort("visitor-1");
        CountDownLatch ensureEntered = new CountDownLatch(1);
        Mockito.when(sessionPort.ensureLiveView("visitor-1")).thenAnswer(invocation -> {
            ensureEntered.countDown();
            return KernelBrowserLiveView.builder().browserLiveViewUrl("https://live.example").build();
        });
        CountDownLatch resetEntered = new CountDownLatch(1);
        Mockito.doAnswer(invocation -> {
            resetEntered.countDown();
            return null;
        }).when(sessionPort).deleteForOwner("visitor-1");
        KernelBrowserTool tool = kernelTool("visitor-1", cli, sessionPort, lock);
        KernelBrowserApplicationService service = new KernelBrowserApplicationService(provider(sessionPort), lock);

        CompletableFuture<Object> call = CompletableFuture.supplyAsync(() -> tool.execute(command()));
        Assert.assertTrue(cliEntered.get().await(1, TimeUnit.SECONDS));
        CompletableFuture<KernelBrowserLiveView> ensure = CompletableFuture.supplyAsync(() -> service.ensure("visitor-1"));
        Assert.assertFalse(ensureEntered.await(200, TimeUnit.MILLISECONDS));
        Assert.assertTrue(service.status("visitor-1").isExists());
        release.get().countDown();
        Assert.assertFalse(failed(call.get(1, TimeUnit.SECONDS)));
        Assert.assertNotNull(ensure.get(1, TimeUnit.SECONDS));

        cliEntered.set(new CountDownLatch(1));
        release.set(new CountDownLatch(1));
        call = CompletableFuture.supplyAsync(() -> tool.execute(command()));
        Assert.assertTrue(cliEntered.get().await(1, TimeUnit.SECONDS));
        CompletableFuture<Void> reset = CompletableFuture.runAsync(() -> service.reset("visitor-1"));
        Assert.assertFalse(resetEntered.await(200, TimeUnit.MILLISECONDS));
        release.get().countDown();
        call.get(1, TimeUnit.SECONDS);
        reset.get(1, TimeUnit.SECONDS);
        Mockito.verify(sessionPort).ensureLiveView("visitor-1");
        Mockito.verify(sessionPort).deleteForOwner("visitor-1");
    }

    @Test
    public void shouldFailEnsureWhenLockWaitTimesOutWithoutMutatingSession() {
        KernelBrowserSessionPort sessionPort = kernelPort("visitor-1");
        BrowserOperationLockPort lock = new BrowserOperationLockPort() {
            @Override
            public <T> T execute(BrowserOperationKind kind, String owner, long waitMillis, Callable<T> action) {
                throw new BrowserOperationLockTimeoutException("timeout");
            }
        };
        KernelBrowserApplicationService service = new KernelBrowserApplicationService(provider(sessionPort), lock);
        try {
            service.ensure("visitor-1");
            Assert.fail("ensure should fail when the lock wait times out");
        } catch (IllegalStateException expected) {
            Assert.assertEquals("云端浏览器正被占用，请稍后重试", expected.getMessage());
        }
        Mockito.verify(sessionPort, Mockito.never()).ensureLiveView(Mockito.any());
    }

    @Test
    public void shouldNotStartCliWhenLockWaitTimesOutAndShouldReleaseAfterFailures() throws Exception {
        InProcessBrowserOperationLockAdapter lock = new InProcessBrowserOperationLockAdapter();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger execs = new AtomicInteger();
        CliExecutionPort cli = Mockito.mock(CliExecutionPort.class);
        Mockito.doAnswer(invocation -> {
            execs.incrementAndGet();
            entered.countDown();
            if (!release.await(2, TimeUnit.SECONDS)) {
                throw new AssertionError("CLI was not released");
            }
            return success();
        }).when(cli).exec(Mockito.any(CliInvocation.class));
        BrowserTool tool = browserTool("visitor-1", cli, onlineRelay("visitor-1"), lock);
        CompletableFuture<Object> first = CompletableFuture.supplyAsync(() -> tool.execute(command()));
        try {
            Assert.assertTrue(entered.await(1, TimeUnit.SECONDS));
            ToolResultPayload timedOut = (ToolResultPayload) tool.execute(Map.of(
                    "args", List.of("browser", "list"),
                    "timeout_ms", 100));
            Assert.assertEquals("browser_lock_timeout", timedOut.getErrorMsg());
            Assert.assertEquals(1, execs.get());
        } finally {
            release.countDown();
        }
        Assert.assertFalse(failed(first.get(1, TimeUnit.SECONDS)));

        CliExecutionPort failing = Mockito.mock(CliExecutionPort.class);
        Mockito.when(failing.exec(Mockito.any(CliInvocation.class)))
                .thenThrow(new RuntimeException("start failed"))
                .thenReturn(success());
        BrowserTool recovering = browserTool("visitor-1", failing, onlineRelay("visitor-1"), lock);
        try {
            recovering.execute(command());
            Assert.fail("CLI startup failure should propagate");
        } catch (RuntimeException expected) {
            Assert.assertEquals("start failed", expected.getMessage());
        }
        ToolResultPayload recovered = (ToolResultPayload) recovering.execute(Map.of(
                "args", List.of("browser", "list"),
                "timeout_ms", 300));
        Assert.assertFalse(Boolean.TRUE.equals(recovered.getFailed()));
        Mockito.verify(failing, Mockito.times(2)).exec(Mockito.any(CliInvocation.class));

        CliExecutionPort kernelCli = Mockito.mock(CliExecutionPort.class);
        Mockito.when(kernelCli.exec(Mockito.any(CliInvocation.class)))
                .thenThrow(new RuntimeException("start failed"))
                .thenReturn(success());
        KernelBrowserTool kernel = kernelTool("visitor-1", kernelCli, kernelPort("visitor-1"), lock);
        ToolResultPayload kernelFailed = (ToolResultPayload) kernel.execute(command());
        Assert.assertEquals("opencli_execution_failed", kernelFailed.getErrorMsg());
        ToolResultPayload kernelRecovered = (ToolResultPayload) kernel.execute(Map.of(
                "args", List.of("browser", "list"),
                "timeout_ms", 300));
        Assert.assertFalse(Boolean.TRUE.equals(kernelRecovered.getFailed()));
        Mockito.verify(kernelCli, Mockito.times(2)).exec(Mockito.any(CliInvocation.class));
    }

    @Test
    public void shouldPassOnlyRemainingBudgetToCli() throws Exception {
        BrowserOperationLockPort delaying = new BrowserOperationLockPort() {
            @Override
            public <T> T execute(BrowserOperationKind kind, String owner, long waitMillis, Callable<T> action) {
                try {
                    Thread.sleep(200L);
                    return action.call();
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        };
        CliExecutionPort cli = Mockito.mock(CliExecutionPort.class);
        Mockito.when(cli.exec(Mockito.any(CliInvocation.class))).thenReturn(success());
        browserTool("visitor-1", cli, onlineRelay("visitor-1"), delaying)
                .execute(Map.of("args", List.of("browser", "list"), "timeout_ms", 1_000));
        ArgumentCaptor<CliInvocation> browserCaptor = ArgumentCaptor.forClass(CliInvocation.class);
        Mockito.verify(cli).exec(browserCaptor.capture());
        Assert.assertTrue(browserCaptor.getValue().getTimeoutMs() > 0);
        Assert.assertTrue(browserCaptor.getValue().getTimeoutMs() <= 850);

        CliExecutionPort kernelCli = Mockito.mock(CliExecutionPort.class);
        Mockito.when(kernelCli.exec(Mockito.any(CliInvocation.class))).thenReturn(success());
        kernelTool("visitor-1", kernelCli, kernelPort("visitor-1"), delaying)
                .execute(Map.of("args", List.of("browser", "list"), "timeout_ms", 1_000));
        ArgumentCaptor<CliInvocation> kernelCaptor = ArgumentCaptor.forClass(CliInvocation.class);
        Mockito.verify(kernelCli).exec(kernelCaptor.capture());
        Assert.assertTrue(kernelCaptor.getValue().getTimeoutMs() > 0);
        Assert.assertTrue(kernelCaptor.getValue().getTimeoutMs() <= 850);
    }

    @Test
    public void shouldFailClosedWhenLockIsMissingAndSkipCliWhenRelayIsOffline() {
        CliExecutionPort cli = Mockito.mock(CliExecutionPort.class);
        ToolResultPayload browserResult = (ToolResultPayload) browserTool(
                "visitor-1", cli, onlineRelay("visitor-1"), null).execute(command());
        Assert.assertEquals("browser_lock_unavailable", browserResult.getErrorMsg());
        ToolResultPayload kernelResult = (ToolResultPayload) kernelTool(
                "visitor-1", cli, kernelPort("visitor-1"), null).execute(command());
        Assert.assertEquals("browser_lock_unavailable", kernelResult.getErrorMsg());

        BrowserRelayPort offline = Mockito.mock(BrowserRelayPort.class);
        Mockito.when(offline.isOnline("visitor-1")).thenReturn(false);
        ToolResultPayload offlineResult = (ToolResultPayload) browserTool(
                "visitor-1", cli, offline, new InProcessBrowserOperationLockAdapter()).execute(command());
        Assert.assertEquals("browser_offline", offlineResult.getErrorMsg());
        Mockito.verify(cli, Mockito.never()).exec(Mockito.any());
    }

    @Test
    public void shouldNotBlockHostCliBehindBrowserLock() throws Exception {
        InProcessBrowserOperationLockAdapter lock = new InProcessBrowserOperationLockAdapter();
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = new Thread(() -> lock.execute(BrowserOperationKind.USER_BROWSER, "visitor-1", 2_000L, () -> {
            held.countDown();
            if (!release.await(2, TimeUnit.SECONDS)) {
                throw new AssertionError("browser lock was not released");
            }
            return null;
        }));
        holder.start();
        Assert.assertTrue(held.await(1, TimeUnit.SECONDS));
        Path workspace = Files.createTempDirectory("host-cli-lock");
        try {
            CliExecutionPort cli = Mockito.mock(CliExecutionPort.class);
            Mockito.when(cli.isResolvable("git")).thenReturn(true);
            Mockito.when(cli.exec(Mockito.any(CliInvocation.class))).thenReturn(success());
            HostCliTool hostCli = new HostCliTool(List.of("git"));
            hostCli.setAgentContext(AgentContext.builder()
                    .requestId("req-1")
                    .sessionId("session-1")
                    .visitorId("visitor-1")
                    .workspaceRoot(workspace.toString())
                    .runtimeDependencies(ReactorRuntimeTestSupport.runtimeDependencies(new ReactorConfig()).toBuilder()
                            .cliExecutionPort(cli)
                            .hostCliProperties(new HostCliProperties())
                            .browserOperationLockPort(lock)
                            .build())
                    .build());
            ToolResultPayload payload = (ToolResultPayload) hostCli.execute(Map.of(
                    "tool", "git",
                    "args", List.of("status")));
            Assert.assertFalse(Boolean.TRUE.equals(payload.getFailed()));
            Mockito.verify(cli).exec(Mockito.any(CliInvocation.class));
        } finally {
            release.countDown();
            holder.join(1_000L);
            Files.deleteIfExists(workspace);
        }
    }

    private static void assertOverlapped(CliGate gate, Callable<Object> firstCall, Callable<Object> secondCall)
            throws Exception {
        CompletableFuture<Object> first = CompletableFuture.supplyAsync(() -> callUnchecked(firstCall));
        CompletableFuture<Object> second = CompletableFuture.supplyAsync(() -> callUnchecked(secondCall));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (gate.maxActive.get() < 2 && System.nanoTime() < deadline) {
            Thread.sleep(20L);
        }
        gate.release.countDown();
        Assert.assertFalse(failed(first.get(1, TimeUnit.SECONDS)));
        Assert.assertFalse(failed(second.get(1, TimeUnit.SECONDS)));
        Assert.assertEquals(2, gate.maxActive.get());
    }

    private static Object callUnchecked(Callable<Object> call) {
        try {
            return call.call();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean failed(Object payload) {
        return Boolean.TRUE.equals(((ToolResultPayload) payload).getFailed());
    }

    private static Map<String, Object> command() {
        return Map.of("args", List.of("browser", "list"));
    }

    private static BrowserRelayPort onlineRelay(String visitorId) {
        BrowserRelayPort relay = Mockito.mock(BrowserRelayPort.class);
        Mockito.when(relay.isOnline(visitorId)).thenReturn(true);
        return relay;
    }

    private static KernelBrowserSession session(String owner) {
        return KernelBrowserSession.builder()
                .ownerKey(owner)
                .kernelSessionId("session-" + owner)
                .kernelBrowserName("rb-" + owner)
                .cdpWsUrl("wss://proxy.kernel.example/" + owner)
                .reconstructed(false)
                .build();
    }

    private static KernelBrowserSessionPort kernelPort(String owner) {
        KernelBrowserSessionPort sessionPort = Mockito.mock(KernelBrowserSessionPort.class);
        Mockito.when(sessionPort.isConfigured()).thenReturn(true);
        Mockito.when(sessionPort.resolveForOwner(owner)).thenReturn(session(owner));
        Mockito.when(sessionPort.statusForOwner(owner)).thenReturn(KernelBrowserSessionStatus.builder()
                .exists(true)
                .kernelSessionId("session-" + owner)
                .reconstructed(false)
                .build());
        return sessionPort;
    }

    private static BrowserTool browserTool(String visitorId,
                                           CliExecutionPort cli,
                                           BrowserRelayPort relay,
                                           BrowserOperationLockPort lock) {
        BrowserTool tool = new BrowserTool();
        tool.setAgentContext(context(visitorId, cli, relay, null, lock));
        return tool;
    }

    private static KernelBrowserTool kernelTool(String visitorId,
                                                CliExecutionPort cli,
                                                KernelBrowserSessionPort sessionPort,
                                                BrowserOperationLockPort lock) {
        KernelBrowserTool tool = new KernelBrowserTool();
        tool.setAgentContext(context(visitorId, cli, null, sessionPort, lock));
        return tool;
    }

    private static AgentContext context(String visitorId,
                                        CliExecutionPort cli,
                                        BrowserRelayPort relay,
                                        KernelBrowserSessionPort sessionPort,
                                        BrowserOperationLockPort lock) {
        OpenCliProperties properties = new OpenCliProperties();
        properties.setEnabled(true);
        properties.setCommand("node");
        properties.setPrefixArgs(List.of("main.js"));
        properties.setCacheDir("vis_cache");
        return AgentContext.builder()
                .requestId("req-1")
                .sessionId("session-1")
                .visitorId(visitorId)
                .workspaceRoot(System.getProperty("java.io.tmpdir"))
                .runtimeDependencies(ReactorRuntimeTestSupport.runtimeDependencies(new ReactorConfig()).toBuilder()
                        .browserRelayPort(relay)
                        .kernelBrowserSessionPort(sessionPort)
                        .cliExecutionPort(cli)
                        .openCliProperties(properties)
                        .browserOperationLockPort(lock)
                        .build())
                .build();
    }

    private static CliResult success() {
        return CliResult.builder()
                .ok(true)
                .tool("opencli")
                .exitCode(0)
                .stdout("{\"ok\":true}")
                .stderr("")
                .durationMs(5)
                .timedOut(false)
                .truncated(false)
                .artifacts(List.of())
                .build();
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = Mockito.mock(ObjectProvider.class);
        Mockito.when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }

    private static final class CliGate {
        private final AtomicInteger maxActive = new AtomicInteger();
        private final CountDownLatch release = new CountDownLatch(1);
        private final CliExecutionPort cli = Mockito.mock(CliExecutionPort.class);

        private CliGate() {
            AtomicInteger active = new AtomicInteger();
            Mockito.doAnswer(invocation -> {
                int current = active.incrementAndGet();
                maxActive.updateAndGet(previous -> Math.max(previous, current));
                try {
                    if (!release.await(2, TimeUnit.SECONDS)) {
                        throw new AssertionError("CLI was not released");
                    }
                    return success();
                } finally {
                    active.decrementAndGet();
                }
            }).when(cli).exec(Mockito.any(CliInvocation.class));
        }
    }
}
