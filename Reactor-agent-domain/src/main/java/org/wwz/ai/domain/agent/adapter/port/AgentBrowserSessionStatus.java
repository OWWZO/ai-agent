package org.wwz.ai.domain.agent.adapter.port;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class AgentBrowserSessionStatus {

    boolean exists;

    String agentSessionId;

    LocalDateTime lastUsedAt;

    boolean reconstructed;
}
