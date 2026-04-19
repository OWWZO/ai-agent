package org.wwz.ai.domain.agent.reactor.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话记忆快照实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentSessionMemory {

    private Long id;
    private Long conversationId;
    private String sessionId;
    private Integer agentType;
    private String summaryText;
    private String factsJson;
    private String artifactRefsJson;
    private Long boundaryMessageId;
    private Integer boundarySortOrder;
    private Integer sourceTurnCount;
    private LocalDateTime lastCompactedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
