package org.wwz.ai.domain.agent.runtime.subagent;

import lombok.Builder;
import lombok.Data;

/**
 * 子 Agent 终结结果。
 */
@Data
@Builder
public class SubAgentResult {

    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_FAILED = "failed";

    private String status;
    private String agentId;
    private String agentType;
    private String description;
    private String prompt;
    /** 回传主 Agent 的结论文本 */
    private String content;
    private int totalToolUseCount;
    private long totalDurationMs;
    private String errorMsg;

    public boolean isCompleted() {
        return STATUS_COMPLETED.equals(status);
    }
}
