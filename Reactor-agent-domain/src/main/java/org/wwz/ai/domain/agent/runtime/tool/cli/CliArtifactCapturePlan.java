package org.wwz.ai.domain.agent.runtime.tool.cli;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * 一次 CLI 文件产物调用的边界计划。
 * <p>
 * 计划只保存 workspace 相对路径；绝对路径仅供同一进程内的捕获和物化使用，不能进入
 * 工具结果、LLM observation 或 Ledger metadata。
 */
@Value
@Builder
public class CliArtifactCapturePlan {

    List<String> originalArgs;
    List<String> rewrittenArgs;
    boolean artifactCommand;
    String requestedOutputRoot;
    String captureRoot;
    String captureRootAbsolute;
    boolean outputRootInjected;
    String captureId;
    boolean valid;
    String errorCode;
    String errorMessage;

    public boolean requiresCaptureLease() {
        return valid && artifactCommand && captureRootAbsolute != null;
    }

    public static CliArtifactCapturePlan ordinary(List<String> originalArgs, List<String> rewrittenArgs) {
        return CliArtifactCapturePlan.builder()
                .originalArgs(copy(originalArgs))
                .rewrittenArgs(copy(rewrittenArgs))
                .artifactCommand(false)
                .valid(true)
                .build();
    }

    public static CliArtifactCapturePlan invalid(List<String> originalArgs,
                                                 List<String> rewrittenArgs,
                                                 boolean artifactCommand,
                                                 String errorCode,
                                                 String errorMessage) {
        return CliArtifactCapturePlan.builder()
                .originalArgs(copy(originalArgs))
                .rewrittenArgs(copy(rewrittenArgs))
                .artifactCommand(artifactCommand)
                .valid(false)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }

    private static List<String> copy(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
