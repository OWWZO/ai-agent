package org.wwz.ai.domain.agent.adapter.port;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class BrowserRelayStatus {

    boolean connected;
    String tabUrl;
    String tabTitle;
}
