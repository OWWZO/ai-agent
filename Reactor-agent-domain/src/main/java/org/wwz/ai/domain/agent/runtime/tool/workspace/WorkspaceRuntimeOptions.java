package org.wwz.ai.domain.agent.runtime.tool.workspace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话工作区运行时配置。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceRuntimeOptions {

    /**
     * 是否启用工作区文件工具。
     */
    @Builder.Default
    private boolean enabled = false;

    /**
     * 工作区根目录模板，支持 {sessionId} 占位。
     */
    @Builder.Default
    private String rootTemplate = "{repoRoot}/reactor-tool/skilloutput/{sessionId}";

    @Builder.Default
    private int maxReadChars = 12000;

    @Builder.Default
    private int maxListEntries = 200;

    @Builder.Default
    private int maxGlobResults = 100;

    @Builder.Default
    private int maxGrepMatches = 100;

    @Builder.Default
    private int maxWriteChars = 200000;
}
