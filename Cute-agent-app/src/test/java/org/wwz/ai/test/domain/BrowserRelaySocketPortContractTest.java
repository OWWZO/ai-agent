package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.application.agent.browser.BrowserRelayApplicationService;
import org.wwz.ai.domain.agent.adapter.port.BrowserRelaySocketPort;
import org.wwz.ai.domain.agent.browser.model.BrowserCommand;
import org.wwz.ai.domain.agent.browser.model.BrowserCommandResult;
import org.wwz.ai.domain.agent.browser.model.BrowserRelayConnection;
import org.wwz.ai.domain.agent.browser.model.BrowserRelayInboundMessage;
import org.wwz.ai.domain.agent.browser.model.BrowserTabMetadata;
import org.wwz.ai.infrastructure.browserrelay.adapter.BrowserRelaySocketAdapter;
import org.wwz.ai.trigger.http.browser.BrowserRelayHub;
import org.wwz.ai.types.agent.config.BrowserRelayProperties;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class BrowserRelaySocketPortContractTest {

    @Test
    public void shouldKeepHubDependentOnlyOnApplicationService() {
        Assert.assertArrayEquals(
                new Class<?>[]{BrowserRelayApplicationService.class},
                BrowserRelayHub.class.getDeclaredConstructors()[0].getParameterTypes()
        );
        Assert.assertEquals(0, BrowserRelayHub.class.getInterfaces().length);
    }

    @Test
    public void shouldForwardTypedRpcAndLifecycleThroughTransportNeutralConnection() throws Exception {
        BrowserRelaySocketAdapter adapter = new BrowserRelaySocketAdapter(new BrowserRelayProperties());
        FakeConnection connection = new FakeConnection("user-1", "connection-1");
        adapter.register(connection);

        Assert.assertTrue(adapter.isOnline("user-1"));
        adapter.accept(BrowserRelayInboundMessage.tabChanged(
                "user-1",
                "connection-1",
                new BrowserTabMetadata("https://example.test/page", "Example")
        ));
        Assert.assertEquals("https://example.test/page", adapter.status("user-1").getTabUrl());
        Assert.assertEquals("Example", adapter.status("user-1").getTabTitle());

        CompletableFuture<BrowserCommandResult> pending = CompletableFuture.supplyAsync(
                () -> adapter.execute(BrowserCommand.of(
                        "user-1", "exec", Map.of("id", "rpc-1"), Duration.ofSeconds(2))));
        Assert.assertTrue(connection.sent.await(1, TimeUnit.SECONDS));
        Assert.assertEquals("rpc-1", connection.command.get().rpcId());
        Assert.assertEquals("exec", connection.command.get().action().value());

        adapter.accept(BrowserRelayInboundMessage.commandResult(
                "user-1",
                "connection-1",
                BrowserCommandResult.success("rpc-1", Map.of(
                        "result", "done",
                        "url", "https://example.test/result"
                ))
        ));
        BrowserCommandResult result = pending.get(1, TimeUnit.SECONDS);
        Assert.assertTrue(result.isOk());
        Assert.assertEquals("done", ((Map<?, ?>) result.getData()).get("result"));
        Assert.assertEquals("https://example.test/result", adapter.status("user-1").getTabUrl());

        adapter.disconnect("user-1");
        Assert.assertFalse(adapter.isOnline("user-1"));
        Assert.assertTrue(connection.terminated.get());
    }

    @Test
    public void shouldConvertTransportSendFailureToRpcFailure() {
        BrowserRelaySocketAdapter adapter = new BrowserRelaySocketAdapter(new BrowserRelayProperties());
        FakeConnection connection = new FakeConnection("user-1", "connection-1");
        connection.sendFailure = new Exception("send failed");
        adapter.register(connection);

        BrowserCommandResult result = adapter.execute(
                BrowserCommand.of("user-1", "exec", Map.of(), Duration.ofSeconds(1))
        );

        Assert.assertFalse(result.isOk());
        Assert.assertEquals("rpc_failed", result.getErrorCode());
        Assert.assertEquals("send failed", result.getError());
    }

    @Test
    public void shouldReplaceConnectionAndCleanItsPendingCommands() throws Exception {
        BrowserRelaySocketAdapter adapter = new BrowserRelaySocketAdapter(new BrowserRelayProperties());
        FakeConnection first = new FakeConnection("user-1", "connection-1");
        FakeConnection second = new FakeConnection("user-1", "connection-2");
        adapter.register(first);

        CompletableFuture<BrowserCommandResult> pending = CompletableFuture.supplyAsync(
                () -> adapter.execute(BrowserCommand.of(
                        "user-1", "exec", Map.of("id", "rpc-replaced"), Duration.ofSeconds(5))));
        Assert.assertTrue(first.sent.await(1, TimeUnit.SECONDS));

        adapter.register(second);

        BrowserCommandResult result = pending.get(1, TimeUnit.SECONDS);
        Assert.assertEquals("browser_offline", result.getErrorCode());
        Assert.assertTrue(first.terminated.get());
        Assert.assertTrue(adapter.isOnline("user-1"));
    }

    @Test
    public void shouldKeepTimedOutRpcCorrelatedDuringLateResultGrace() throws Exception {
        BrowserRelaySocketAdapter adapter = new BrowserRelaySocketAdapter(new BrowserRelayProperties());
        FakeConnection connection = new FakeConnection("user-1", "connection-1");
        adapter.register(connection);
        BrowserCommand command = BrowserCommand.of(
                "user-1", "exec", Map.of("id", "rpc-late"), Duration.ofMillis(50));

        BrowserCommandResult timedOut = adapter.execute(command);
        Assert.assertEquals("command_result_unknown", timedOut.getErrorCode());

        BrowserCommandResult duplicate = adapter.execute(command);
        Assert.assertEquals("duplicate_rpc_id", duplicate.getErrorCode());

        adapter.accept(BrowserRelayInboundMessage.commandResult(
                "user-1",
                "connection-1",
                BrowserCommandResult.success("rpc-late", "late result")
        ));
        BrowserCommandResult afterLate = adapter.execute(command);
        Assert.assertEquals("command_result_unknown", afterLate.getErrorCode());
    }

    @Test
    public void shouldIgnoreResultFromAnotherUserOrConnection() throws Exception {
        BrowserRelaySocketAdapter adapter = new BrowserRelaySocketAdapter(new BrowserRelayProperties());
        FakeConnection connection = new FakeConnection("user-1", "connection-1");
        adapter.register(connection);
        CompletableFuture<BrowserCommandResult> pending = CompletableFuture.supplyAsync(
                () -> adapter.execute(BrowserCommand.of(
                        "user-1", "exec", Map.of("id", "rpc-owner"), Duration.ofMillis(60))));
        Assert.assertTrue(connection.sent.await(1, TimeUnit.SECONDS));

        adapter.accept(BrowserRelayInboundMessage.commandResult(
                "user-2",
                "connection-1",
                BrowserCommandResult.success("rpc-owner", "wrong user")
        ));
        adapter.accept(BrowserRelayInboundMessage.commandResult(
                "user-1",
                "connection-2",
                BrowserCommandResult.success("rpc-owner", "wrong connection")
        ));

        Assert.assertEquals("command_result_unknown", pending.get(1, TimeUnit.SECONDS).getErrorCode());
    }

    private static final class FakeConnection implements BrowserRelayConnection {

        private final String userId;
        private final String id;
        private final CountDownLatch sent = new CountDownLatch(1);
        private final AtomicReference<BrowserCommand> command = new AtomicReference<>();
        private final AtomicBoolean terminated = new AtomicBoolean();
        private Exception sendFailure;

        private FakeConnection(String userId, String id) {
            this.userId = userId;
            this.id = id;
        }

        @Override
        public String userId() {
            return userId;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public boolean isOpen() {
            return !terminated.get();
        }

        @Override
        public void send(BrowserCommand command) throws Exception {
            if (sendFailure != null) {
                throw sendFailure;
            }
            this.command.set(command);
            sent.countDown();
        }

        @Override
        public void terminate() {
            terminated.set(true);
        }
    }
}
