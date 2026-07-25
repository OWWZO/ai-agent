package org.wwz.ai.domain.agent.runtime.tool.common.shell;

import lombok.Builder;
import lombok.Data;

/**
 * 本地 shell 执行结果。
 */
@Data
@Builder
public class ShellExecResult {

    private String stdout;
    private String stderr;
    private int exitCode;
    private boolean timedOut;
    private long durationMs;
}
