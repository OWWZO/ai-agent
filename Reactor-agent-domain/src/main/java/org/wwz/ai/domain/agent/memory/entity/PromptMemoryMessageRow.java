package org.wwz.ai.domain.agent.memory.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.wwz.ai.domain.agent.runtime.enums.RoleType;

import java.time.LocalDateTime;

/**
 * 提示词记忆消息的持久化行，toolCalls 以 JSON 保留其结构。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptMemoryMessageRow {

    private Long id;
    private Long turnId;
    private Integer seqNo;
    private RoleType role;
    private String content;
    private String base64Image;
    private String toolCallId;
    private String toolCallsJson;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
