package org.wwz.ai.domain.agent.reactor.model.history;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 单轮请求详情，events 是历史回放的唯一权威来源。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationTurnDetail {

    private String requestId;
    private Integer sortOrder;
    private String query;
    private Object files;
    private Integer agentType;
    private String response;
    private Integer status;
    private Integer forceStop;
    private Object metrics;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private List<ConversationEventDetail> events;
}
