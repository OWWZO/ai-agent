package org.wwz.ai.domain.agent.adapter.port.cli;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class CliResult {
    boolean ok;
    String tool;
    int exitCode;
    String stdout;
    String stderr;
    long durationMs;
    boolean timedOut;
    boolean truncated;
    List<CliArtifact> artifacts;
}
