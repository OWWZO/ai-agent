package org.wwz.ai.domain.agent.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Fix 角色只读投影
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FixRoleVO {

    /**
     * 角色ID
     */
    private String agentId;

    /**
     * 角色名称
     */
    private String agentName;

    /**
     * 角色描述
     */
    private String description;

    /**
     * 是否默认角色
     */
    private boolean defaultRole;

    /**
     * 排序值
     */
    private Integer sortIndex;

    /**
     * 流程步骤数
     */
    private Integer flowStepCount;
}
