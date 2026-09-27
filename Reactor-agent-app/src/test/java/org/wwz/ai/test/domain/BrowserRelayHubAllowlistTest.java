package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.wwz.ai.domain.agent.adapter.port.BrowserRpcResult;
import org.wwz.ai.trigger.http.browser.BrowserRelayHub;
import org.wwz.ai.types.agent.config.BrowserRelayProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class BrowserRelayHubAllowlistTest {

    @Test
    public void shouldRejectUnknownHubActions() {
        BrowserRelayHub hub = new BrowserRelayHub(new BrowserRelayProperties());
        WebSocketSession session = Mockito.mock(WebSocketSession.class);
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("visitorId", "visitor-1");
        Mockito.when(session.isOpen()).thenReturn(true);
        Mockito.when(session.getId()).thenReturn("s1");
        Mockito.when(session.getAttributes()).thenReturn(attrs);
        hub.register("visitor-1", session);

        BrowserRpcResult eval = hub.call("visitor-1", "eval", Map.of(), Duration.ofSeconds(5));
        Assert.assertFalse(eval.isOk());
        Assert.assertEquals("unsupported_action", eval.getErrorCode());

        BrowserRpcResult site = hub.call("visitor-1", "site", Map.of(), Duration.ofSeconds(5));
        Assert.assertFalse(site.isOk());
        Assert.assertEquals("unsupported_action", site.getErrorCode());

        BrowserRpcResult cookies = hub.call("visitor-1", "cookies", Map.of(), Duration.ofMillis(20));
        Assert.assertNotEquals("unsupported_action", cookies.getErrorCode());

        BrowserRpcResult bind = hub.call("visitor-1", "bind", Map.of(), Duration.ofMillis(20));
        Assert.assertNotEquals("unsupported_action", bind.getErrorCode());
    }

    @Test
    public void shouldAcceptLeaseReleaseWithoutExtension() {
        BrowserRelayHub hub = new BrowserRelayHub(new BrowserRelayProperties());
        BrowserRpcResult released = hub.call("visitor-1", "lease-release", Map.of(), Duration.ofSeconds(1));
        Assert.assertTrue(released.isOk());
    }

    @Test
    public void shouldCompleteRpcWhenExtensionReturnsScalarData() throws Exception {
        BrowserRelayHub hub = new BrowserRelayHub(new BrowserRelayProperties());
        WebSocketSession session = Mockito.mock(WebSocketSession.class);
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("visitorId", "visitor-1");
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
        hub.register("visitor-1", session);

        CompletableFuture<BrowserRpcResult> call = CompletableFuture.supplyAsync(
                () -> hub.call("visitor-1", "exec", Map.of(), Duration.ofSeconds(5)));
        Assert.assertTrue(sent.await(1, TimeUnit.SECONDS));
        String id = com.alibaba.fastjson.JSON.parseObject(outgoing.get().getPayload()).getString("id");
        hub.onText(session, com.alibaba.fastjson.JSON.toJSONString(Map.of(
                "id", id,
                "ok", true,
                "data", "plain browser result"
        )));

        BrowserRpcResult result = call.get(1, TimeUnit.SECONDS);
        Assert.assertTrue(result.isOk());
        Assert.assertEquals("plain browser result", result.getData());
    }
}
