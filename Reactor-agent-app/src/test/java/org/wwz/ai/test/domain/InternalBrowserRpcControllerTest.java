package org.wwz.ai.test.domain;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.wwz.ai.application.agent.browser.BrowserRelayApplicationService;
import org.wwz.ai.domain.agent.browser.model.BrowserCommand;
import org.wwz.ai.domain.agent.browser.model.BrowserCommandResult;
import org.wwz.ai.trigger.http.browser.InternalBrowserRpcController;
import org.wwz.ai.types.agent.config.BrowserRelayProperties;

import java.util.Map;

public class InternalBrowserRpcControllerTest {

    @Test
    public void shouldAcceptLegacyVisitorIdFromRelayClient() {
        BrowserRelayApplicationService applicationService = Mockito.mock(BrowserRelayApplicationService.class);
        Mockito.when(applicationService.execute(Mockito.any(BrowserCommand.class)))
                .thenReturn(BrowserCommandResult.success("rpc-1", null));
        InternalBrowserRpcController controller = new InternalBrowserRpcController(
                applicationService,
                new BrowserRelayProperties()
        );
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        Map<String, Object> response = controller.rpc(
                "{\"id\":\"rpc-1\",\"visitorId\":\"user-1\",\"action\":\"navigate\",\"url\":\"https://x.com\"}",
                request);

        Assert.assertEquals(Boolean.TRUE, response.get("ok"));
        ArgumentCaptor<BrowserCommand> command = ArgumentCaptor.forClass(BrowserCommand.class);
        Mockito.verify(applicationService).execute(command.capture());
        Assert.assertEquals("user-1", command.getValue().userId());
        Assert.assertEquals("navigate", command.getValue().action().value());
        Assert.assertFalse(command.getValue().parameters().containsKey("visitorId"));
    }
}
