package org.wwz.ai.domain.agent.ledger.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolStructuredOutput;

import java.time.LocalDateTime;

/**
 * 工具调用查询视图。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolInvocationView {

    private Long id;

    private Long runId;

    private Long llmInvocationId;

    private String requestId;

    private String sessionId;

    private String userId;

    private String toolCallId;

    /** 父 Agent 工具 toolCallId；子 Agent 嵌套工具重放用 */
    private String parentToolCallId;

    private String subAgentId;

    private String subAgentType;

    private String subAgentDescription;

    private Integer dispatchIndex;

    private String agentName;

    private Integer stepNo;

    private String toolName;

    private String toolProvider;

    private String inputJson;

    private String llmObservation;

    /** 工具完整结果 JSON（历史回放用；历史行为 null，读取侧回落到 llmObservation） */
    private String toolResult;

    /**
     * rich tool 强类型输出。
     */
    private ToolStructuredOutput structuredOutput;

    private Integer status;

    private String errorMsg;

    private Long durationMs;

    private Integer artifactCount;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private LocalDateTime createTime;
}
