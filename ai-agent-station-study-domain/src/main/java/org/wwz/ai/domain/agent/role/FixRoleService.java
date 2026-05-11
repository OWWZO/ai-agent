package org.wwz.ai.domain.agent.role;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.adapter.repository.IAgentRepository;
import org.wwz.ai.domain.agent.model.valobj.AiAgentVO;
import org.wwz.ai.domain.agent.model.valobj.FixRoleVO;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * Fix 角色领域服务 todo:后续开发功能点：基于角色特有提示词模板 通过拖拉编排的方式动态组装agent
 */
@Slf4j
@Service
public class FixRoleService implements IFixRoleService {

    @Resource
    private IAgentRepository repository;

    @Resource
    private ReactorConfig reactorConfig;

    @Override
    public List<FixRoleVO> queryAvailableRoles() {
        List<AiAgentVO> aiAgentVOS = repository.queryAvailableFixRoles();
        if (aiAgentVOS == null || aiAgentVOS.isEmpty()) {
            return List.of();
        }

        String defaultRoleId = resolveConfiguredDefaultRoleId();
        FixRoleVO configuredDefaultRole = null;
        List<FixRoleVO> sortedRoles = new ArrayList<>();
        for (int i = 0; i < aiAgentVOS.size(); i++) {
            AiAgentVO aiAgentVO = aiAgentVOS.get(i);
            FixRoleVO roleVO = toFixRoleVO(aiAgentVO, false, i);
            if (defaultRoleId != null && defaultRoleId.equals(aiAgentVO.getAgentId())) {
                configuredDefaultRole = roleVO;
                continue;
            }
            sortedRoles.add(roleVO);
        }

        if (configuredDefaultRole != null) {
            configuredDefaultRole.setDefaultRole(true);
            configuredDefaultRole.setSortIndex(0);
            List<FixRoleVO> result = new ArrayList<>();
            result.add(configuredDefaultRole);
            for (int i = 0; i < sortedRoles.size(); i++) {
                sortedRoles.get(i).setSortIndex(i + 1);
            }
            result.addAll(sortedRoles);
            return result;
        }

        FixRoleVO fallbackDefaultRole = sortedRoles.get(0);
        fallbackDefaultRole.setDefaultRole(true);
        fallbackDefaultRole.setSortIndex(0);
        for (int i = 1; i < sortedRoles.size(); i++) {
            sortedRoles.get(i).setSortIndex(i);
        }
        return sortedRoles;
    }

    @Override
    public FixRoleVO queryDefaultRole() {
        List<FixRoleVO> roles = queryAvailableRoles();
        return roles.isEmpty() ? null : roles.get(0);
    }

    @Override
    public FixRoleVO queryRole(String aiAgentId) {
        if (aiAgentId == null || aiAgentId.isBlank()) {
            return null;
        }

        AiAgentVO aiAgentVO = repository.queryAvailableFixRoleByAgentId(aiAgentId);
        if (aiAgentVO == null) {
            return null;
        }

        FixRoleVO defaultRole = queryDefaultRole();
        boolean isDefaultRole = defaultRole != null && aiAgentId.equals(defaultRole.getAgentId());
        return toFixRoleVO(aiAgentVO, isDefaultRole, isDefaultRole ? 0 : null);
    }


    private FixRoleVO toFixRoleVO(AiAgentVO aiAgentVO, boolean defaultRole, Integer sortIndex) {
        return FixRoleVO.builder()
                .agentId(aiAgentVO.getAgentId())
                .agentName(aiAgentVO.getAgentName())
                .description(aiAgentVO.getDescription())
                .defaultRole(defaultRole)
                .sortIndex(sortIndex)
                .flowStepCount(aiAgentVO.getFlowStepCount())
                .build();
    }

    private String resolveConfiguredDefaultRoleId() {
        String defaultRoleId = reactorConfig.getChatDefaultRoleId();
        if (defaultRoleId == null || defaultRoleId.isBlank()) {
            return null;
        }
        return defaultRoleId.trim();
    }
}
