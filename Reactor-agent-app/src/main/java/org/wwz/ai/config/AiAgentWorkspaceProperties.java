package org.wwz.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 会话工作区（cwd 模式）配置。
 */
@Data
@ConfigurationProperties(prefix = "autobots.autoagent.workspace")
public class AiAgentWorkspaceProperties {

    /**
     * 是否启用 workspace_* 工具，并在 agent 可见工具中下线 file_tool。
     */
    private boolean enabled = true;

    /**
     * 工作区根目录模板，支持 {sessionId}、${java.io.tmpdir}、${user.dir}。
     */
    private String rootTemplate = "${java.io.tmpdir}/reactor-agent-workspace/{sessionId}";

    private int maxReadChars = 12000;

    private int maxListEntries = 200;

    private int maxGlobResults = 100;

    private int maxGrepMatches = 100;

    private int maxWriteChars = 200000;
}
