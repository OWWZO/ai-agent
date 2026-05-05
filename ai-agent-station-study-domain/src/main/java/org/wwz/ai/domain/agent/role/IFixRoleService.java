package org.wwz.ai.domain.agent.role;

import org.wwz.ai.domain.agent.model.valobj.FixRoleVO;

import java.util.List;

/**
 * Fix 角色领域服务
 */
public interface IFixRoleService {

    /**
     * 查询可用角色列表
     */
    List<FixRoleVO> queryAvailableRoles();

    /**
     * 查询默认角色
     */
    FixRoleVO queryDefaultRole();

    /**
     * 查询指定角色
     */
    FixRoleVO queryRole(String aiAgentId);

}
