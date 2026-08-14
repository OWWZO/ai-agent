package org.wwz.ai.domain.agent.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 工作记忆消息行（投影表 ai_agent_working_memory_message）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkingMemoryMessage {

    private Long id;
    private String sessionId;
    /** 与 turn.memory_scope 一致，便于按 scope 查询 */
    private String memoryScope;
    private Long turnId;
    private String requestId;
    private Long runId;
    private Integer seqNo;
    private String role;
    private String content;
    /** 模型原生 CoT，passback / hydrate 用 */
    private String reasoningContent;
    private String toolCallId;
    private String toolCallsJson;
    private String base64Image;
    private String messageKind;
    private String visibility;
    private Integer tokenEstimate;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
