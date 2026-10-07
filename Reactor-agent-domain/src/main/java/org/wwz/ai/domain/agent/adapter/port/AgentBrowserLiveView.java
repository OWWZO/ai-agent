package org.wwz.ai.domain.agent.adapter.port;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AgentBrowserLiveView {

    String browserLiveViewUrl;

    String agentSessionId;

    boolean reconstructed;
}
