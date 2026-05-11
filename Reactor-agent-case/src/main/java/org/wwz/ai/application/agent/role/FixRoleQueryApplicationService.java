package org.wwz.ai.application.agent.role;

import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.model.valobj.FixRoleVO;
import org.wwz.ai.domain.agent.role.IFixRoleService;

import javax.annotation.Resource;
import java.util.List;

/**
 * Fix 角色查询应用服务。
 * 当前仅承接 trigger -> domain 的查询编排，后续角色治理逻辑继续在本层扩展。
 */
@Service
public class FixRoleQueryApplicationService implements IFixRoleQueryService {

    @Resource
    private IFixRoleService fixRoleService;

    @Override
    public List<FixRoleVO> queryAvailableRoles() {
        return fixRoleService.queryAvailableRoles();
    }

    @Override
    public FixRoleVO queryDefaultRole() {
        return fixRoleService.queryDefaultRole();
    }

    @Override
    public FixRoleVO queryRole(String aiAgentId) {
        return fixRoleService.queryRole(aiAgentId);
    }
}
