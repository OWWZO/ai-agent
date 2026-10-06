package org.wwz.ai.test.domain;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.wwz.ai.domain.agent.adapter.port.BrowserRpcResult;
import org.wwz.ai.trigger.http.browser.BrowserRelayHub;
import org.wwz.ai.trigger.http.browser.InternalBrowserRpcController;
import org.wwz.ai.types.agent.config.BrowserRelayProperties;

import java.time.Duration;
import java.util.Map;

public class InternalBrowserRpcControllerTest {

    @Test
    public void shouldAcceptLegacyVisitorIdFromRelayClient() {
        BrowserRelayHub hub = Mockito.mock(BrowserRelayHub.class);
        Mockito.when(hub.call(Mockito.eq("user-1"), Mockito.eq("navigate"), Mockito.anyMap(), Mockito.any(Duration.class)))
                .thenReturn(BrowserRpcResult.builder().ok(true).build());
        InternalBrowserRpcController controller = new InternalBrowserRpcController(hub, new BrowserRelayProperties());
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        Map<String, Object> response = controller.rpc(
                "{\"id\":\"rpc-1\",\"visitorId\":\"user-1\",\"action\":\"navigate\",\"url\":\"https://x.com\"}",
                request);

        Assert.assertEquals(Boolean.TRUE, response.get("ok"));
        ArgumentCaptor<Map> params = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(hub).call(Mockito.eq("user-1"), Mockito.eq("navigate"), params.capture(), Mockito.any(Duration.class));
        Assert.assertFalse(params.getValue().containsKey("visitorId"));
    }
}
