package org.wwz.ai.domain.agent.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话角色摘要
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationRoleVO {

    /**
     * 角色ID
     */
    private String agentId;

    /**
     * 角色名称
     */
    private String agentName;

    /**
     * 当前是否可用
     */
    private boolean available;

    /**
     * 是否默认角色
     */
    private boolean defaultRole;
}
