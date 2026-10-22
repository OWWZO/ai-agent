package org.wwz.ai.application.agent.browser;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class BrowserPairingRecord {

    String userId;
    String token;
    String code;
    String relayUrl;
    long codeExpiresAt;
}
