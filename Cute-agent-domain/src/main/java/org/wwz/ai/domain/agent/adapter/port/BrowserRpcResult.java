package org.wwz.ai.domain.agent.adapter.port;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class BrowserRpcResult {

    boolean ok;
    String error;
    String errorCode;
    String page;
    Object data;
}
