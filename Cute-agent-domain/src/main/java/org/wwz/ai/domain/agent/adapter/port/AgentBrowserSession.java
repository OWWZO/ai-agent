package org.wwz.ai.domain.agent.adapter.port;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AgentBrowserSession {

    String userId;

    String agentSessionId;

    String agentBrowserName;

    String cdpWsUrl;

    boolean reconstructed;
}
