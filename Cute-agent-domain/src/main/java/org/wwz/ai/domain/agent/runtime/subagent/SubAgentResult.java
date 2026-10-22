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
    /**
     * 子 Agent 对应的真实子会话 ID（{@code sub_{agentId}}）。
     * 落库失败时可能为 null —— 前端据此判断是否可打开只读子会话详情。
     */
    private String childSessionId;
    /** 回传主 Agent 的结论文本 */
    private String content;
    private int totalToolUseCount;
    private long totalDurationMs;
    private String errorMsg;
    /** 本轮结束时工作记忆是否成功写入；resume 前主 Agent 应检查此标记 */
    private Boolean memoryPersisted;

    public boolean isCompleted() {
        return STATUS_COMPLETED.equals(status);
    }
}
