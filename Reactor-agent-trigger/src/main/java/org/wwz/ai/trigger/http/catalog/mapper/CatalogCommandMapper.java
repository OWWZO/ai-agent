package org.wwz.ai.trigger.http.catalog.mapper;

import org.springframework.stereotype.Component;
import org.wwz.ai.api.dto.CatalogMcpCreateRequestDTO;
import org.wwz.ai.api.dto.CatalogModelCreateRequestDTO;
import org.wwz.ai.api.dto.CatalogSubAgentCreateRequestDTO;
import org.wwz.ai.application.catalog.mcp.CatalogMcpCreateCommand;
import org.wwz.ai.application.catalog.model.CatalogModelCreateCommand;
import org.wwz.ai.application.catalog.skill.SkillCreateCommand;
import org.wwz.ai.application.catalog.skill.SkillImportCommand;
import org.wwz.ai.application.catalog.skill.SkillPackageCommand;
import org.wwz.ai.application.catalog.subagent.CatalogSubAgentCreateCommand;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Catalog HTTP DTO 与应用命令的转换。 */
@Component
public class CatalogCommandMapper {

    public static final int MAX_PACKAGE_BYTES = SkillPackageCommand.MAX_PACKAGE_BYTES;

    public CatalogModelCreateCommand toModelCreateCommand(CatalogModelCreateRequestDTO request) {
        if (request == null) {
            return null;
        }
        return new CatalogModelCreateCommand(
                request.getApiId(),
                request.getBaseUrl(),
                request.getApiKey(),
                request.getCompletionsPath(),
                request.getEmbeddingsPath(),
                request.getModelId(),
                request.getModelName(),
                request.getModelType(),
                request.getModelUsage(),
                request.getSupportsThinking(),
                request.getContextWindow(),
                request.getStatus());
    }

    public CatalogMcpCreateCommand toMcpCreateCommand(CatalogMcpCreateRequestDTO request) {
        if (request == null) {
            return null;
        }
        return new CatalogMcpCreateCommand(
                request.getMcpId(),
                request.getMcpName(),
                request.getTransportType(),
                request.getTransportConfig(),
                request.getRequestTimeout(),
                request.getStatus());
    }

    public CatalogSubAgentCreateCommand toSubAgentCreateCommand(CatalogSubAgentCreateRequestDTO request) {
        if (request == null) {
            return null;
        }
        return new CatalogSubAgentCreateCommand(
                request.getAgentKey(),
                request.getDisplayName(),
                request.getWhenToUse(),
                request.getSystemPrompt(),
                toSet(request.getAllowedTools()),
                toSet(request.getDisallowedTools()),
                request.getToolPolicyMode(),
                toSet(request.getDeferredTools()),
                request.getMaxSteps(),
                request.getStatus());
    }

    public SkillCreateCommand toSkillCreateCommand(String name, String description, String content) {
        return new SkillCreateCommand(name, description, content);
    }

    public SkillImportCommand toSkillImportCommand(String url) {
        return new SkillImportCommand(url);
    }

    public SkillPackageCommand toSkillPackageCommand(byte[] zipBytes, String originalFilename) {
        return new SkillPackageCommand(zipBytes, originalFilename);
    }

    private static Set<String> toSet(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        Set<String> result = new LinkedHashSet<>();
        values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .forEach(result::add);
        return result.isEmpty() ? null : result;
    }
}
