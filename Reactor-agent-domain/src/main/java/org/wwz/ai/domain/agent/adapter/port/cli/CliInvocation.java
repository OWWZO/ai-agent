package org.wwz.ai.domain.agent.adapter.port.cli;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Value
@Builder
public class CliInvocation {
    String tool;
    List<String> args;
    String cwd;
    Map<String, String> env;
    String stdin;
    long timeoutMs;
    String capture;
    int maxOutputChars;
}
