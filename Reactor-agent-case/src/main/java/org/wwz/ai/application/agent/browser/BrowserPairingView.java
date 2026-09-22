package org.wwz.ai.application.agent.browser;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class BrowserPairingView {

    String token;
    String code;
    String relayUrl;
    long expiresAt;
}
