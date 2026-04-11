package org.wwz.ai.trigger.http.agent.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ConversationListRespVO {
    private Long id;
    private String sessionId;
    private String title;
    private Integer agentType;
    private String productType;
    private Integer messageCount;
    private Integer pinned;
    private String lastMessagePreview;
    private ConversationRoleRespVO role;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
