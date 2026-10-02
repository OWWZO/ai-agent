package org.wwz.ai.domain.agent.adapter.port;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class KernelBrowserLiveView {

    String browserLiveViewUrl;

    String kernelSessionId;

    boolean reconstructed;
}
