package org.wwz.ai.application.catalog;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.api.ICatalogService;
import org.wwz.ai.api.dto.CatalogCapabilitiesResponseDTO;
import org.wwz.ai.api.dto.CatalogMcpResponseDTO;
import org.wwz.ai.api.dto.CatalogSubAgentCreateRequestDTO;
import org.wwz.ai.api.dto.CatalogModelResponseDTO;
import org.wwz.ai.api.dto.CatalogSkillResponseDTO;
import org.wwz.ai.api.dto.CatalogSubAgentResponseDTO;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.application.agent.subagent.SubAgentDefinitionAdminApplicationService;
import org.wwz.ai.domain.agent.adapter.repository.IAgentRepository;
import org.wwz.ai.domain.agent.adapter.repository.ISubAgentDefinitionRepository;
import org.wwz.ai.domain.agent.model.valobj.AiClientToolMcpVO;
import org.wwz.ai.domain.agent.runtime.llm.LlmModelCatalog;
import org.wwz.ai.domain.agent.runtime.llm.LlmModelCatalogEntry;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentDefinitionUpsertCommand;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentDefinitionRecord;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillCatalog;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillDescriptor;
import org.wwz.ai.types.enums.ResponseCode;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 登录用户目录查询应用服务。
 */
@Service
@RequiredArgsConstructor
public class CatalogApplicationService implements ICatalogService {

    private final LlmModelCatalog llmModelCatalog;
    private final ISubAgentDefinitionRepository subAgentDefinitionRepository;
    private final IAgentRepository agentRepository;
    private final SkillCatalog skillCatalog;
    private final SubAgentDefinitionAdminApplicationService subAgentDefinitionAdminApplicationService;

    @Override
    public Response<List<CatalogModelResponseDTO>> listModels() {
        List<CatalogModelResponseDTO> data = llmModelCatalog.listUserSelectableModels().stream()
                .map(this::toModelResponse)
                .toList();
        return success(data);
    }

    @Override
    public Response<List<CatalogSubAgentResponseDTO>> listSubAgents() {
        List<SubAgentDefinitionRecord> records = subAgentDefinitionRepository.listAll();
        List<CatalogSubAgentResponseDTO> data = records == null
                ? List.of()
                : records.stream().map(this::toSubAgentResponse).toList();
        return success(data);
    }

    @Override
    public Response<Boolean> createSubAgent(CatalogSubAgentCreateRequestDTO request) {
        SubAgentDefinitionUpsertCommand command = request == null
                ? null
                : SubAgentDefinitionUpsertCommand.builder()
                .agentKey(request.getAgentKey())
                .displayName(request.getDisplayName())
                .whenToUse(request.getWhenToUse())
                .systemPrompt(request.getSystemPrompt())
                .allowedTools(toSet(request.getAllowedTools()))
                .disallowedTools(toSet(request.getDisallowedTools()))
                .toolPolicyMode(request.getToolPolicyMode())
                .deferredTools(toSet(request.getDeferredTools()))
                .maxSteps(request.getMaxSteps())
                .status(request.getStatus())
                .build();
        return success(subAgentDefinitionAdminApplicationService.create(command));
    }

    @Override
    public Response<CatalogCapabilitiesResponseDTO> listCapabilities() {
        return success(CatalogCapabilitiesResponseDTO.builder()
                .skills(listSkills())
                .mcps(listMcps())
                .build());
    }

    private List<CatalogSkillResponseDTO> listSkills() {
        if (skillCatalog == null || !skillCatalog.isEnabled()) {
            return List.of();
        }
        List<SkillDescriptor> descriptors = skillCatalog.list();
        if (descriptors == null || descriptors.isEmpty()) {
            return List.of();
        }
        return descriptors.stream()
                .filter(descriptor -> descriptor != null && StringUtils.isNotBlank(descriptor.getName()))
                .map(descriptor -> CatalogSkillResponseDTO.builder()
                        .name(descriptor.getName())
                        .description(descriptor.getDescription())
                        .sourceSummary(resolveSkillSource(descriptor))
                        .status(1)
                        .build())
                .toList();
    }

    private List<CatalogMcpResponseDTO> listMcps() {
        List<AiClientToolMcpVO> mcps = agentRepository.queryAllAiClientToolMcpVOList();
        if (mcps == null || mcps.isEmpty()) {
            return List.of();
        }
        return mcps.stream()
                .filter(mcp -> mcp != null && StringUtils.isNotBlank(mcp.getMcpId()))
                .map(mcp -> CatalogMcpResponseDTO.builder()
                        .mcpId(mcp.getMcpId())
                        .mcpName(mcp.getMcpName())
                        .transportType(mcp.getTransportType())
                        .status(mcp.getStatus() == null ? 1 : mcp.getStatus())
                        .build())
                .toList();
    }

    private CatalogModelResponseDTO toModelResponse(LlmModelCatalogEntry entry) {
        return CatalogModelResponseDTO.builder()
                .modelId(entry.getModelId())
                .modelName(entry.getModelName())
                .modelType(entry.getModelType())
                .supportsThinking(entry.getSupportsThinking())
                .contextWindow(entry.getContextWindow())
                .status(entry.getStatus())
                .build();
    }

    private CatalogSubAgentResponseDTO toSubAgentResponse(SubAgentDefinitionRecord record) {
        return CatalogSubAgentResponseDTO.builder()
                .agentKey(record.getAgentKey())
                .displayName(record.getDisplayName())
                .whenToUse(record.getWhenToUse())
                .toolSummary(toolSummary(record))
                .toolPolicyMode(record.getToolPolicyMode())
                .maxSteps(record.getMaxSteps())
                .status(record.getStatus())
                .build();
    }

    private List<String> toolSummary(SubAgentDefinitionRecord record) {
        Set<String> summary = new LinkedHashSet<>();
        if (record.getAllowedTools() == null || record.getAllowedTools().isEmpty()) {
            summary.add("*");
        } else {
            summary.addAll(record.getAllowedTools());
        }
        if (record.getDisallowedTools() != null) {
            record.getDisallowedTools().stream()
                    .filter(StringUtils::isNotBlank)
                    .map(tool -> "!" + tool.trim())
                    .forEach(summary::add);
        }
        return List.copyOf(summary);
    }

    private String resolveSkillSource(SkillDescriptor descriptor) {
        if (StringUtils.isNotBlank(descriptor.getSource())) {
            return descriptor.getSource();
        }
        return "runtime skill registry";
    }

    private static Set<String> toSet(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        Set<String> result = new LinkedHashSet<>();
        values.stream()
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .forEach(result::add);
        return result.isEmpty() ? null : result;
    }

    private static <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }
}
