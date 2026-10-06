package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.wwz.ai.application.agent.browser.BrowserPairingView;
import org.wwz.ai.application.agent.browser.BrowserRelayApplicationService;
import org.wwz.ai.domain.agent.adapter.port.BrowserRelayPort;
import org.wwz.ai.types.agent.config.BrowserRelayProperties;

public class BrowserRelayApplicationServiceTest {

    @Test
    public void shouldIssueAndClaimPairingCode() {
        BrowserRelayProperties properties = new BrowserRelayProperties();
        @SuppressWarnings("unchecked")
        ObjectProvider<BrowserRelayPort> provider = Mockito.mock(ObjectProvider.class);
        Mockito.when(provider.getIfAvailable()).thenReturn(null);
        BrowserRelayApplicationService service = new BrowserRelayApplicationService(properties, provider);

        BrowserPairingView issued = service.createPairing("user-1", "ws://localhost:8100/api/agent/browser/relay");
        Assert.assertEquals(6, issued.getCode().length());
        Assert.assertEquals("user-1", service.resolveUserId(issued.getToken()));

        BrowserPairingView claimed = service.claim(issued.getCode());
        Assert.assertEquals(issued.getToken(), claimed.getToken());
        Assert.assertEquals(issued.getRelayUrl(), claimed.getRelayUrl());
        Assert.assertEquals(issued.getToken(), service.claim(issued.getCode()).getToken());
        Assert.assertEquals("user-1", service.resolveUserId(issued.getToken()));
    }
}
