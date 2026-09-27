package org.wwz.ai.domain.agent.adapter.port.cli;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CliArtifact {
    String path;
    Long size;
    String mime;
}
