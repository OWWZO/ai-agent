package org.wwz.ai.application.catalog.skill;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.application.catalog.ApplicationResult;
import org.wwz.ai.domain.agent.catalog.model.CatalogSkill;
import org.wwz.ai.domain.agent.catalog.model.CatalogSkillPackageResult;
import org.wwz.ai.domain.agent.catalog.port.ICatalogSkillPackagePort;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillCatalog;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillDescriptor;
import org.wwz.ai.types.enums.ResponseCode;

import java.util.List;
import java.util.Map;

/**
 * Skill 目录查询与技能包操作用例。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillCatalogApplicationService {

    private final SkillCatalog skillCatalog;
    private final ICatalogSkillPackagePort skillPackagePort;

    public ApplicationResult<List<CatalogSkill>> listSkills() {
        if (skillCatalog == null || !skillCatalog.isEnabled()) {
            return ApplicationResult.success(List.of());
        }
        List<SkillDescriptor> descriptors = skillCatalog.list();
        if (descriptors == null) {
            return ApplicationResult.success(List.of());
        }
        return ApplicationResult.success(descriptors.stream()
                .filter(descriptor -> descriptor != null && StringUtils.isNotBlank(descriptor.getName()))
                .map(descriptor -> CatalogSkill.builder()
                        .name(descriptor.getName())
                        .description(descriptor.getDescription())
                        .sourceSummary(StringUtils.isNotBlank(descriptor.getSource())
                                ? descriptor.getSource()
                                : "runtime skill registry")
                        .status(1)
                        .build())
                .toList());
    }

    public ApplicationResult<Map<String, Object>> previewSkillPackage(SkillPackageCommand command) {
        try {
            validateZip(command);
            return ApplicationResult.success(skillPackagePort.previewZip(command.zipBytes()).data());
        } catch (Exception e) {
            return failure(e, "技能包解析失败");
        }
    }

    public ApplicationResult<Map<String, Object>> uploadSkill(SkillPackageCommand command) {
        try {
            validateZip(command);
            String filename = command.originalFilename() == null ? "skill.zip" : command.originalFilename();
            return ApplicationResult.success(skillPackagePort.installZip(command.zipBytes(), filename, false).data());
        } catch (Exception e) {
            return failure(e, "技能包安装失败");
        }
    }

    public ApplicationResult<Map<String, Object>> createSkill(SkillCreateCommand command) {
        try {
            CatalogSkillPackageResult result = skillPackagePort.installFromMarkdown(
                    command == null ? null : command.name(),
                    command == null ? null : command.description(),
                    command == null ? null : command.content(),
                    false);
            return ApplicationResult.success(result.data());
        } catch (Exception e) {
            return failure(e, "技能创建失败");
        }
    }

    public ApplicationResult<Map<String, Object>> importSkill(SkillImportCommand command) {
        try {
            CatalogSkillPackageResult result = skillPackagePort.installFromUrl(
                    command == null ? null : command.url(), false);
            return ApplicationResult.success(result.data());
        } catch (Exception e) {
            return failure(e, "技能导入失败");
        }
    }

    private static void validateZip(SkillPackageCommand command) {
        if (command == null || command.zipBytes() == null || command.zipBytes().length == 0) {
            throw new IllegalArgumentException("技能包不能为空");
        }
        if (command.zipBytes().length > SkillPackageCommand.MAX_PACKAGE_BYTES) {
            throw new IllegalArgumentException("技能包不能超过 32MB");
        }
    }

    private static ApplicationResult<Map<String, Object>> failure(Exception exception, String fallback) {
        String message = StringUtils.isBlank(exception.getMessage()) ? fallback : exception.getMessage();
        return ApplicationResult.failure(ResponseCode.ILLEGAL_PARAMETER, message, null);
    }
}
