package org.wwz.ai.application.catalog.subagent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.application.catalog.ApplicationResult;
import org.wwz.ai.domain.agent.adapter.repository.ISubAgentDefinitionRepository;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentDefinition;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentDefinitionLoader;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentDefinitionRecord;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentDefinitionUpsertCommand;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentRegistry;
import org.wwz.ai.domain.agent.catalog.model.CatalogSubAgent;
import org.wwz.ai.types.enums.ResponseCode;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * SubAgent 安全目录查询与创建用例。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubAgentCatalogApplicationService {

    private static final Pattern AGENT_KEY_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_/-]{1,62}$");

    private final ISubAgentDefinitionRepository repository;
    private final SubAgentDefinitionLoader loader;

    public ApplicationResult<List<CatalogSubAgent>> listSubAgents() {
        try {
            List<SubAgentDefinitionRecord> records = repository.listAll();
            if (records == null) {
                return ApplicationResult.success(List.of());
            }
            return ApplicationResult.success(records.stream()
                    .filter(record -> record != null && StringUtils.isNotBlank(record.getAgentKey()))
                    .map(SubAgentCatalogApplicationService::toCatalogSubAgent)
                    .toList());
        } catch (Exception e) {
            log.error("查询 SubAgent 目录失败", e);
            return ApplicationResult.failure(ResponseCode.UN_ERROR, ResponseCode.UN_ERROR.getInfo(), null);
        }
    }

    public ApplicationResult<CatalogSubAgent> queryByAgentKey(String agentKey) {
        try {
            Optional<SubAgentDefinitionRecord> record = StringUtils.isBlank(agentKey)
                    ? Optional.empty()
                    : repository.findByAgentKey(agentKey.trim());
            return ApplicationResult.success(record.map(SubAgentCatalogApplicationService::toCatalogSubAgent).orElse(null));
        } catch (Exception e) {
            log.error("查询 SubAgent 目录项失败", e);
            return ApplicationResult.failure(ResponseCode.UN_ERROR, ResponseCode.UN_ERROR.getInfo(), null);
        }
    }

    public ApplicationResult<Boolean> createSubAgent(CatalogSubAgentCreateCommand command) {
        try {
            validate(command);
            String key = command.agentKey().trim();
            if (repository.findByAgentKey(key).isPresent()) {
                throw new IllegalArgumentException("agentKey 已存在: " + key);
            }
            boolean created = repository.insert(normalize(command));
            if (created) {
                loader.reload();
            }
            return ApplicationResult.success(created);
        } catch (IllegalArgumentException e) {
            return ApplicationResult.failure(ResponseCode.ILLEGAL_PARAMETER, e.getMessage(), false);
        } catch (Exception e) {
            log.error("创建 SubAgent 失败", e);
            return ApplicationResult.failure(ResponseCode.UN_ERROR, ResponseCode.UN_ERROR.getInfo(), false);
        }
    }

    private static CatalogSubAgent toCatalogSubAgent(SubAgentDefinitionRecord record) {
        return CatalogSubAgent.builder()
                .agentKey(record.getAgentKey())
                .displayName(record.getDisplayName())
                .whenToUse(record.getWhenToUse())
                .allowedTools(record.getAllowedTools())
                .disallowedTools(record.getDisallowedTools())
                .toolPolicyMode(record.getToolPolicyMode())
                .maxSteps(record.getMaxSteps())
                .status(record.getStatus())
                .build();
    }

    private static SubAgentDefinitionUpsertCommand normalize(CatalogSubAgentCreateCommand command) {
        Integer status = command.status() == null ? 1 : (command.status() == 0 ? 0 : 1);
        return SubAgentDefinitionUpsertCommand.builder()
                .agentKey(command.agentKey().trim())
                .displayName(StringUtils.trimToNull(command.displayName()))
                .whenToUse(command.whenToUse().trim())
                .systemPrompt(command.systemPrompt())
                .allowedTools(command.allowedTools())
                .disallowedTools(command.disallowedTools())
                .toolPolicyMode(StringUtils.defaultIfBlank(command.toolPolicyMode(),
                        SubAgentDefinition.TOOL_POLICY_INHERIT).trim().toLowerCase())
                .deferredTools(command.deferredTools())
                .maxSteps(command.maxSteps())
                .status(status)
                .build();
    }

    private static void validate(CatalogSubAgentCreateCommand command) {
        if (command == null || StringUtils.isBlank(command.agentKey())) {
            throw new IllegalArgumentException("agentKey 不能为空");
        }
        String key = command.agentKey().trim();
        if (!AGENT_KEY_PATTERN.matcher(key).matches()) {
            throw new IllegalArgumentException("agentKey 格式非法：字母开头，仅含字母数字_-/长度2-63");
        }
        if (SubAgentRegistry.TYPE_GENERAL_PURPOSE.equalsIgnoreCase(key)) {
            throw new IllegalArgumentException("禁止覆盖内置子 Agent: " + key);
        }
        if (StringUtils.isBlank(command.whenToUse())) {
            throw new IllegalArgumentException("whenToUse 不能为空");
        }
        if (StringUtils.isBlank(command.systemPrompt())) {
            throw new IllegalArgumentException("systemPrompt 不能为空");
        }
        if (command.maxSteps() != null && command.maxSteps() <= 0) {
            throw new IllegalArgumentException("maxSteps 必须为正整数或留空");
        }
        String toolPolicyMode = StringUtils.defaultIfBlank(command.toolPolicyMode(),
                SubAgentDefinition.TOOL_POLICY_INHERIT).trim().toLowerCase();
        if (!SubAgentDefinition.TOOL_POLICY_INHERIT.equals(toolPolicyMode)
                && !SubAgentDefinition.TOOL_POLICY_CUSTOM.equals(toolPolicyMode)) {
            throw new IllegalArgumentException("toolPolicyMode 只能是 inherit 或 custom");
        }
    }
}
