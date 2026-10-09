package org.wwz.ai.infrastructure.browserrelay.adapter;

import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.adapter.port.BrowserRelayPort;
import org.wwz.ai.domain.agent.adapter.port.BrowserRelaySocketPort;
import org.wwz.ai.domain.agent.adapter.port.BrowserRelayStatus;
import org.wwz.ai.domain.agent.adapter.port.BrowserRpcResult;
import org.wwz.ai.domain.agent.browser.model.BrowserCommand;
import org.wwz.ai.domain.agent.browser.model.BrowserCommandResult;
import org.wwz.ai.domain.agent.browser.model.BrowserConnectionStatus;

import java.time.Duration;
import java.util.Map;

/**
 * Compatibility adapter for the Agent-tool outbound BrowserRelayPort.
 *
 * <p>The outbound contract remains separate from the inbound WebSocket
 * application seam. It delegates all command policy and transport work to the
 * typed socket port.</p>
 */
@Component
@ConditionalOnBean(BrowserRelaySocketPort.class)
public class BrowserRelayPortAdapter implements BrowserRelayPort {

    private final BrowserRelaySocketPort socketPort;

    public BrowserRelayPortAdapter(BrowserRelaySocketPort socketPort) {
        this.socketPort = socketPort;
    }

    @Override
    public boolean isOnline(String userId) {
        return socketPort.isOnline(userId);
    }

    @Override
    public BrowserRelayStatus status(String userId) {
        BrowserConnectionStatus status = socketPort.status(userId);
        return BrowserRelayStatus.builder()
                .connected(status.isConnected())
                .tabUrl(status.getTabUrl())
                .tabTitle(status.getTabTitle())
                .build();
    }

    @Override
    public BrowserRpcResult call(String userId, String action, Map<String, Object> params, Duration timeout) {
        if (StringUtils.isBlank(action)) {
            if (!socketPort.isOnline(userId)) {
                return BrowserRpcResult.builder()
                        .ok(false)
                        .errorCode("browser_offline")
                        .error("浏览器未连接")
                        .build();
            }
            return BrowserRpcResult.builder()
                    .ok(false)
                    .errorCode("unsupported_action")
                    .error("不允许的浏览器动作")
                    .build();
        }
        BrowserCommandResult result = socketPort.execute(BrowserCommand.of(userId, action, params, timeout));
        return BrowserRpcResult.builder()
                .ok(result.ok())
                .error(result.error())
                .errorCode(result.errorCode())
                .page(result.page())
                .data(result.data())
                .build();
    }

    @Override
    public void disconnect(String userId) {
        socketPort.disconnect(userId);
    }
}
