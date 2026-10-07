package org.wwz.ai.application.agent.agentbrowser;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.adapter.port.AgentBrowserLiveView;
import org.wwz.ai.domain.agent.adapter.port.AgentBrowserSessionPort;
import org.wwz.ai.domain.agent.adapter.port.AgentBrowserSessionStatus;


@Service
public class AgentBrowserApplicationService {

    private final AgentBrowserSessionPort agentBrowserSessionPort;

    public AgentBrowserApplicationService(ObjectProvider<AgentBrowserSessionPort> portProvider) {
        this.agentBrowserSessionPort = portProvider.getIfAvailable();
    }

    public AgentBrowserLiveView ensure(String userId) {
        AgentBrowserSessionPort port = requireConfiguredPort(userId);
        return port.ensureLiveView(userId.trim());
    }

    public AgentBrowserSessionStatus status(String userId) {
        if (StringUtils.isBlank(userId)) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        if (agentBrowserSessionPort == null || !agentBrowserSessionPort.isConfigured()) {
            return AgentBrowserSessionStatus.builder()
                    .exists(false)
                    .reconstructed(false)
                    .build();
        }
        return agentBrowserSessionPort.statusForUser(userId);
    }

    public void reset(String userId) {
        AgentBrowserSessionPort port = requireConfiguredPort(userId);
        port.deleteForUser(userId.trim());
    }

    private AgentBrowserSessionPort requireConfiguredPort(String userId) {
        if (StringUtils.isBlank(userId)) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        if (agentBrowserSessionPort == null || !agentBrowserSessionPort.isConfigured()) {
            throw new IllegalStateException("Agent 浏览器未配置，请检查 reactor.agent-browser.enabled 和 KERNEL_API_KEY");
        }
        return agentBrowserSessionPort;
    }
}
