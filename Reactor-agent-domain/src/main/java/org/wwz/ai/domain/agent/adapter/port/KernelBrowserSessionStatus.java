package org.wwz.ai.domain.agent.adapter.port;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class KernelBrowserSessionStatus {

    boolean exists;

    String kernelSessionId;

    LocalDateTime lastUsedAt;

    boolean reconstructed;
}
