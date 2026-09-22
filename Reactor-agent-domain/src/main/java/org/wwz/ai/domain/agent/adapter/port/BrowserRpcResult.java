package org.wwz.ai.domain.agent.adapter.port;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Value
@Builder
public class BrowserRpcResult {

    boolean ok;
    String error;
    String errorCode;
    String page;
    Map<String, Object> data;
}
