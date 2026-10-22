package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.wwz.ai.application.agent.browser.BrowserRelayApplicationService;
import org.wwz.ai.domain.agent.browser.model.BrowserCommandResult;
import org.wwz.ai.infrastructure.browserrelay.adapter.BrowserRelaySocketAdapter;
import org.wwz.ai.trigger.http.browser.BrowserRelayHub;
import org.wwz.ai.types.agent.config.BrowserRelayProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class BrowserRelayHubAllowlistTest {

    private static BrowserRelayHub newHub() {
        BrowserRelaySocketAdapter adapter = new BrowserRelaySocketAdapter(new BrowserRelayProperties());
        return new BrowserRelayHub(new BrowserRelayApplicationService(new BrowserRelayProperties(), adapter));
    }

    @Test
    public void shouldRejectUnknownHubActions() {
        BrowserRelayHub hub = newHub();
        WebSocketSession session = Mockito.mock(WebSocketSession.class);
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("userId", "user-1");
        Mockito.when(session.isOpen()).thenReturn(true);
        Mockito.when(session.getId()).thenReturn("s1");
        Mockito.when(session.getAttributes()).thenReturn(attrs);
        hub.register("user-1", session);

        BrowserCommandResult eval = hub.call("user-1", "eval", Map.of(), Duration.ofSeconds(5));
        Assert.assertFalse(eval.isOk());
        Assert.assertEquals("unsupported_action", eval.getErrorCode());

        BrowserCommandResult site = hub.call("user-1", "site", Map.of(), Duration.ofSeconds(5));
        Assert.assertFalse(site.isOk());
        Assert.assertEquals("unsupported_action", site.getErrorCode());

        BrowserCommandResult cookies = hub.call("user-1", "cookies", Map.of(), Duration.ofMillis(20));
        Assert.assertNotEquals("unsupported_action", cookies.getErrorCode());

        BrowserCommandResult bind = hub.call("user-1", "bind", Map.of(), Duration.ofMillis(20));
        Assert.assertNotEquals("unsupported_action", bind.getErrorCode());
    }

    @Test
    public void shouldAcceptLeaseReleaseWithoutExtension() {
        BrowserRelayHub hub = newHub();
        BrowserCommandResult released = hub.call("user-1", "lease-release", Map.of(), Duration.ofSeconds(1));
        Assert.assertTrue(released.isOk());
    }

    @Test
    public void shouldCompleteRpcWhenExtensionReturnsScalarData() throws Exception {
        BrowserRelayHub hub = newHub();
        WebSocketSession session = Mockito.mock(WebSocketSession.class);
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("userId", "user-1");
        Mockito.when(session.isOpen()).thenReturn(true);
        Mockito.when(session.getId()).thenReturn("s1");
        Mockito.when(session.getAttributes()).thenReturn(attrs);
        AtomicReference<TextMessage> outgoing = new AtomicReference<>();
        CountDownLatch sent = new CountDownLatch(1);
        Mockito.doAnswer(invocation -> {
            outgoing.set(invocation.getArgument(0));
            sent.countDown();
            return null;
        }).when(session).sendMessage(Mockito.any(TextMessage.class));
        hub.register("user-1", session);

        CompletableFuture<BrowserCommandResult> call = CompletableFuture.supplyAsync(
                () -> hub.call("user-1", "exec", Map.of(), Duration.ofSeconds(5)));
        Assert.assertTrue(sent.await(1, TimeUnit.SECONDS));
        String id = com.alibaba.fastjson.JSON.parseObject(outgoing.get().getPayload()).getString("id");
        hub.onText(session, com.alibaba.fastjson.JSON.toJSONString(Map.of(
                "id", id,
                "ok", true,
                "data", "plain browser result"
        )));

        BrowserCommandResult result = call.get(1, TimeUnit.SECONDS);
        Assert.assertTrue(result.isOk());
        Assert.assertEquals("plain browser result", result.getData());
    }

    @Test
    public void shouldAllowConcurrentRpcAndCorrelateReverseResponses() throws Exception {
        BrowserRelayHub hub = newHub();
        WebSocketSession session = Mockito.mock(WebSocketSession.class);
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("userId", "user-1");
        Mockito.when(session.isOpen()).thenReturn(true);
        Mockito.when(session.getId()).thenReturn("s1");
        Mockito.when(session.getAttributes()).thenReturn(attrs);
        List<TextMessage> outgoing = new CopyOnWriteArrayList<>();
        CountDownLatch sent = new CountDownLatch(2);
        Mockito.doAnswer(invocation -> {
            outgoing.add(invocation.getArgument(0));
            sent.countDown();
            return null;
        }).when(session).sendMessage(Mockito.any(TextMessage.class));
        hub.register("user-1", session);

        CompletableFuture<BrowserCommandResult> first = CompletableFuture.supplyAsync(
                () -> hub.call("user-1", "exec", Map.of("id", "rpc-first"), Duration.ofSeconds(5)));
        CompletableFuture<BrowserCommandResult> second = CompletableFuture.supplyAsync(
                () -> hub.call("user-1", "tabs", Map.of("id", "rpc-second"), Duration.ofSeconds(5)));

        Assert.assertTrue(sent.await(1, TimeUnit.SECONDS));
        Assert.assertEquals(2, outgoing.size());
        List<String> ids = outgoing.stream()
                .map(message -> com.alibaba.fastjson.JSON.parseObject(message.getPayload()).getString("id"))
                .toList();
        String firstId = "rpc-first";
        String secondId = "rpc-second";
        Assert.assertEquals(2, ids.size());
        Assert.assertTrue(ids.contains(firstId));
        Assert.assertTrue(ids.contains(secondId));
        Assert.assertNotEquals(firstId, secondId);

        hub.onText(session, com.alibaba.fastjson.JSON.toJSONString(Map.of(
                "id", secondId,
                "ok", true,
                "data", "second result"
        )));
        hub.onText(session, com.alibaba.fastjson.JSON.toJSONString(Map.of(
                "id", firstId,
                "ok", true,
                "data", "first result"
        )));

        BrowserCommandResult firstResult = first.get(1, TimeUnit.SECONDS);
        BrowserCommandResult secondResult = second.get(1, TimeUnit.SECONDS);
        Assert.assertTrue(firstResult.isOk());
        Assert.assertTrue(secondResult.isOk());
        Assert.assertNotEquals("browser_busy", firstResult.getErrorCode());
        Assert.assertNotEquals("browser_busy", secondResult.getErrorCode());
        Assert.assertEquals("first result", firstResult.getData());
        Assert.assertEquals("second result", secondResult.getData());
    }

    @Test
    public void shouldRejectDuplicateRpcIdWithoutReplacingPendingRequest() throws Exception {
        BrowserRelayHub hub = newHub();
        WebSocketSession session = Mockito.mock(WebSocketSession.class);
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("userId", "user-1");
        Mockito.when(session.isOpen()).thenReturn(true);
        Mockito.when(session.getId()).thenReturn("s1");
        Mockito.when(session.getAttributes()).thenReturn(attrs);
        List<TextMessage> outgoing = new CopyOnWriteArrayList<>();
        CountDownLatch sent = new CountDownLatch(1);
        Mockito.doAnswer(invocation -> {
            outgoing.add(invocation.getArgument(0));
            sent.countDown();
            return null;
        }).when(session).sendMessage(Mockito.any(TextMessage.class));
        hub.register("user-1", session);

        CompletableFuture<BrowserCommandResult> original = CompletableFuture.supplyAsync(
                () -> hub.call("user-1", "exec", Map.of("id", "rpc-1"), Duration.ofSeconds(5)));
        Assert.assertTrue(sent.await(1, TimeUnit.SECONDS));

        BrowserCommandResult duplicate = hub.call(
                "user-1", "tabs", Map.of("id", "rpc-1"), Duration.ofSeconds(1));
        Assert.assertFalse(duplicate.isOk());
        Assert.assertEquals("duplicate_rpc_id", duplicate.getErrorCode());
        Assert.assertEquals(1, outgoing.size());

        hub.onText(session, com.alibaba.fastjson.JSON.toJSONString(Map.of(
                "id", "rpc-1",
                "ok", true,
                "data", "original result"
        )));

        BrowserCommandResult result = original.get(1, TimeUnit.SECONDS);
        Assert.assertTrue(result.isOk());
        Assert.assertEquals("original result", result.getData());
    }
}
