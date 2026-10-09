package org.wwz.ai.trigger.http.catalog.mapper;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.wwz.ai.api.dto.CatalogCapabilitiesResponseDTO;
import org.wwz.ai.api.dto.CatalogMcpResponseDTO;
import org.wwz.ai.api.dto.CatalogModelResponseDTO;
import org.wwz.ai.api.dto.CatalogSkillResponseDTO;
import org.wwz.ai.api.dto.CatalogSubAgentResponseDTO;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.application.catalog.ApplicationResult;
import org.wwz.ai.domain.agent.catalog.model.CatalogCapabilities;
import org.wwz.ai.domain.agent.catalog.model.CatalogMcp;
import org.wwz.ai.domain.agent.catalog.model.CatalogModel;
import org.wwz.ai.domain.agent.catalog.model.CatalogSkill;
import org.wwz.ai.domain.agent.catalog.model.CatalogSubAgent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Catalog 应用结果到 HTTP/API 响应 DTO 的转换。 */
@Component
public class CatalogResponseMapper {

    public Response<List<CatalogModelResponseDTO>> toModels(ApplicationResult<List<CatalogModel>> result) {
        return response(result, data -> data == null ? null : data.stream().map(this::toModel).toList());
    }

    public Response<List<CatalogSubAgentResponseDTO>> toSubAgents(
            ApplicationResult<List<CatalogSubAgent>> result) {
        return response(result, data -> data == null ? null : data.stream().map(this::toSubAgent).toList());
    }

    public Response<CatalogCapabilitiesResponseDTO> toCapabilities(
            ApplicationResult<CatalogCapabilities> result) {
        return response(result, this::toCapabilities);
    }

    public Response<Boolean> toBoolean(ApplicationResult<Boolean> result) {
        return response(result, value -> value);
    }

    public Response<Map<String, Object>> toMap(ApplicationResult<Map<String, Object>> result) {
        return response(result, value -> value);
    }

    private CatalogModelResponseDTO toModel(CatalogModel model) {
        return CatalogModelResponseDTO.builder()
                .modelId(model.getModelId())
                .modelName(model.getModelName())
                .modelType(model.getModelType())
                .supportsThinking(model.getSupportsThinking())
                .contextWindow(model.getContextWindow())
                .status(model.getStatus())
                .build();
    }

    private CatalogSubAgentResponseDTO toSubAgent(CatalogSubAgent agent) {
        return CatalogSubAgentResponseDTO.builder()
                .agentKey(agent.getAgentKey())
                .displayName(agent.getDisplayName())
                .whenToUse(agent.getWhenToUse())
                .toolSummary(toolSummary(agent))
                .toolPolicyMode(agent.getToolPolicyMode())
                .maxSteps(agent.getMaxSteps())
                .status(agent.getStatus())
                .build();
    }

    private CatalogCapabilitiesResponseDTO toCapabilities(CatalogCapabilities capabilities) {
        if (capabilities == null) {
            return null;
        }
        List<CatalogSkillResponseDTO> skills = capabilities.getSkills() == null
                ? List.of()
                : capabilities.getSkills().stream().map(this::toSkill).toList();
        List<CatalogMcpResponseDTO> mcps = capabilities.getMcps() == null
                ? List.of()
                : capabilities.getMcps().stream().map(this::toMcp).toList();
        return CatalogCapabilitiesResponseDTO.builder().skills(skills).mcps(mcps).build();
    }

    private CatalogSkillResponseDTO toSkill(CatalogSkill skill) {
        return CatalogSkillResponseDTO.builder()
                .name(skill.getName())
                .description(skill.getDescription())
                .sourceSummary(skill.getSourceSummary())
                .status(skill.getStatus())
                .build();
    }

    private CatalogMcpResponseDTO toMcp(CatalogMcp mcp) {
        return CatalogMcpResponseDTO.builder()
                .mcpId(mcp.mcpId())
                .mcpName(mcp.mcpName())
                .transportType(mcp.transportType())
                .status(mcp.status() == null ? 1 : mcp.status())
                .build();
    }

    private List<String> toolSummary(CatalogSubAgent agent) {
        Set<String> summary = new LinkedHashSet<>();
        if (agent.getAllowedTools() == null || agent.getAllowedTools().isEmpty()) {
            summary.add("*");
        } else {
            summary.addAll(agent.getAllowedTools());
        }
        if (agent.getDisallowedTools() != null) {
            agent.getDisallowedTools().stream()
                    .filter(StringUtils::isNotBlank)
                    .map(tool -> "!" + tool.trim())
                    .forEach(summary::add);
        }
        return new ArrayList<>(summary);
    }

    private <T, R> Response<R> response(ApplicationResult<T> result, Function<T, R> mapper) {
        return Response.<R>builder()
                .code(result.code())
                .info(result.info())
                .data(mapper.apply(result.data()))
                .build();
    }
}
