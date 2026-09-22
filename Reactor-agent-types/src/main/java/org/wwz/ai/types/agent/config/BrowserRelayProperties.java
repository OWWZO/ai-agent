package org.wwz.ai.types.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "reactor.browser-relay")
public class BrowserRelayProperties {

    private boolean enabled = true;

    private String path = "/api/agent/browser/relay";

    private int pairingTtlSeconds = 300;

    private int rpcTimeoutSeconds = 60;
}
