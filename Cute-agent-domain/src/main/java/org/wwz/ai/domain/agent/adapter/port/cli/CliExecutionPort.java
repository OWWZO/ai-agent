package org.wwz.ai.domain.agent.adapter.port.cli;

public interface CliExecutionPort {

    CliResult exec(CliInvocation invocation);

    boolean isResolvable(String tool);
}
