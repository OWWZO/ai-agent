package org.wwz.ai.application.agent.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.wwz.ai.domain.agent.runtime.command.AgentExecutionFile;

import java.util.List;

/**
 * GPT 查询用例命令。由 Trigger request mapper 创建，不进入 Domain runtime。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GptQueryCommand {

    private String query;
    private String sessionId;
    private String requestId;
    private Integer deepThink;
    private String outputStyle;
    private String traceId;
    private String user;
    private String model;
    private Boolean thinking;
    private String thinkingEffort;
    private List<AgentExecutionFile> sessionFiles;
    private Boolean forcePlanMode;
}
