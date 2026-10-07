package org.wwz.ai.domain.agent.adapter.port;

public interface AgentBrowserSessionPort {

    boolean isConfigured();

    AgentBrowserSession resolveForUser(String userId);

    AgentBrowserSessionStatus statusForUser(String userId);

    AgentBrowserLiveView ensureLiveView(String userId);

    void deleteForUser(String userId);
}
