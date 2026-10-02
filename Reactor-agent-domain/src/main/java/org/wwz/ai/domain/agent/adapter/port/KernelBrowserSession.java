package org.wwz.ai.domain.agent.adapter.port;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class KernelBrowserSession {

    String ownerKey;

    String kernelSessionId;

    String kernelBrowserName;

    String cdpWsUrl;

    boolean reconstructed;
}
