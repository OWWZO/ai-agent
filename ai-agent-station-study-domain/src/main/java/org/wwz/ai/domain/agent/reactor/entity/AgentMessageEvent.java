package org.wwz.ai.domain.agent.reactor.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI Agent 消息最终细节表 PO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentMessageEvent {

    private Long id;
    private Long messageId;
    private Integer seqNo;
    private String eventType;
    private String eventSubType;
    private String displayArea;
    private String taskId;
    private Integer taskOrder;
    private String title;
    private String contentText;
    private String payloadJson;
    private String status;
    private LocalDateTime createTime;
    private Integer deleted;
}
