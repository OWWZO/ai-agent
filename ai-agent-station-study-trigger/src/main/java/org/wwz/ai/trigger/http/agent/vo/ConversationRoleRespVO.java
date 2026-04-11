package org.wwz.ai.trigger.http.agent.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 会话角色摘要
 */
@Data
@Builder
public class ConversationRoleRespVO {

    private String agentId;
    private String agentName;
    private boolean available;
    private boolean defaultRole;
}
