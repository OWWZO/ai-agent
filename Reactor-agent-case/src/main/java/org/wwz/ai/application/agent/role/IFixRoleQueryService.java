package org.wwz.ai.application.agent.role;

import org.wwz.ai.domain.agent.model.valobj.FixRoleVO;

import java.util.List;

/**
 * Fix 角色查询应用服务接口。
 * 为 trigger 提供稳定的角色库查询 seam，避免入口层直接依赖 domain/service 根接口。
 */
public interface IFixRoleQueryService {

    List<FixRoleVO> queryAvailableRoles();

    FixRoleVO queryDefaultRole();

    FixRoleVO queryRole(String aiAgentId);
}
