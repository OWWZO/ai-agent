package org.wwz.ai.trigger.http.agent.vo;

import lombok.Builder;
import lombok.Data;

/**
 * Fix 角色列表响应
 */
@Data
@Builder
public class FixRoleRespVO {

    private String agentId;
    private String agentName;
    private String description;
    private boolean defaultRole;
}
