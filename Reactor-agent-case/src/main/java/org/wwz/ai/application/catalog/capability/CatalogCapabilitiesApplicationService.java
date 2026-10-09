package org.wwz.ai.application.catalog.capability;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.wwz.ai.application.catalog.ApplicationResult;
import org.wwz.ai.application.catalog.mcp.McpCatalogApplicationService;
import org.wwz.ai.application.catalog.skill.SkillCatalogApplicationService;
import org.wwz.ai.domain.agent.catalog.model.CatalogCapabilities;
import org.wwz.ai.domain.agent.catalog.model.CatalogMcp;
import org.wwz.ai.domain.agent.catalog.model.CatalogSkill;
import org.wwz.ai.types.enums.ResponseCode;

import java.util.List;

/**
 * 对外能力目录的组合查询用例。
 */
@Service
@RequiredArgsConstructor
public class CatalogCapabilitiesApplicationService {

    private final SkillCatalogApplicationService skillCatalogApplicationService;
    private final McpCatalogApplicationService mcpCatalogApplicationService;

    public ApplicationResult<CatalogCapabilities> listCapabilities() {
        ApplicationResult<List<CatalogSkill>> skills = skillCatalogApplicationService.listSkills();
        if (!isSuccess(skills)) {
            return ApplicationResult.failure(ResponseCode.UN_ERROR, skills.info(), null);
        }
        ApplicationResult<List<CatalogMcp>> mcps = mcpCatalogApplicationService.listMcps();
        if (!isSuccess(mcps)) {
            return ApplicationResult.failure(ResponseCode.UN_ERROR, mcps.info(), null);
        }
        return ApplicationResult.success(CatalogCapabilities.builder()
                .skills(skills.data() == null ? List.of() : skills.data())
                .mcps(mcps.data() == null ? List.of() : mcps.data())
                .build());
    }

    private static boolean isSuccess(ApplicationResult<?> result) {
        return result != null && ResponseCode.SUCCESS.getCode().equals(result.code());
    }
}
