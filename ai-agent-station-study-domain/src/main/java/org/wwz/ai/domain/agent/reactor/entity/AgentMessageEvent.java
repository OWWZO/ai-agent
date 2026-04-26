package org.wwz.ai.domain.agent.reactor.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI Agent 单轮事实块账本 PO。
 * payload_json 只记录未标准化的最小扩展信息，不再承载事件主语义。
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
    private String toolUseId;
    private String toolName;
    private String toolArgumentsJson;
    private String title;
    private String contentText;
    private Boolean referenceOnly;
    private String artifactRefsJson;
    private String structuredDataJson;
    private String payloadJson;
    private String status;
    private LocalDateTime createTime;
    private Integer deleted;
}
