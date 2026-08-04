package org.wwz.ai.domain.agent.memory.ltm;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MemoryPrefetchBlock {
    String providerName;
    String rawText;
    String fencedText;
}
