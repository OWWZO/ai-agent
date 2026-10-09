package org.wwz.ai.infrastructure.adapter.port;

import org.wwz.ai.domain.agent.adapter.port.BrowserRelaySocketPort;
import org.wwz.ai.infrastructure.browserrelay.adapter.BrowserRelayPortAdapter;

/**
 * Source-compatible name for the outbound BrowserRelayPort adapter.
 * New relay logic lives under the browserrelay context package.
 */
@Deprecated
public class BrowserRelayPortImpl extends BrowserRelayPortAdapter {

    public BrowserRelayPortImpl(BrowserRelaySocketPort socketPort) {
        super(socketPort);
    }
}
