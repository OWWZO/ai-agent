package org.wwz.ai.infrastructure.adapter.repository;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;
import org.wwz.ai.domain.agent.adapter.repository.ISubAgentDefinitionRepository;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentDefinition;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentDefinitionRecord;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentDefinitionUpsertCommand;
import org.wwz.ai.infrastructure.dao.ISubAgentDefinitionDao;
import org.wwz.ai.infrastructure.dao.po.SubAgentDefinitionPO;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 可配置子 Agent 定义仓储实现。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class SubAgentDefinitionRepository implements ISubAgentDefinitionRepository {

    private final ISubAgentDefinitionDao subAgentDefinitionDao;

    @Override
    public List<SubAgentDefinition> listEnabled() {
        List<SubAgentDefinitionPO> rows = subAgentDefinitionDao.queryEnabled();
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .map(this::toDefinition)
                .filter(def -> def != null && StringUtils.isNotBlank(def.getAgentType()))
                .collect(Collectors.toList());
    }

    @Override
    public List<SubAgentDefinitionRecord> listAll() {
        List<SubAgentDefinitionPO> rows = subAgentDefinitionDao.queryAll();
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .map(this::toRecord)
                .filter(r -> r != null && StringUtils.isNotBlank(r.getAgentKey()))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<SubAgentDefinitionRecord> findByAgentKey(String agentKey) {
        if (StringUtils.isBlank(agentKey)) {
            return Optional.empty();
        }
        SubAgentDefinitionPO po = subAgentDefinitionDao.queryByAgentKey(agentKey.trim());
        SubAgentDefinitionRecord record = toRecord(po);
        return record == null ? Optional.empty() : Optional.of(record);
    }

    @Override
    public boolean insert(SubAgentDefinitionUpsertCommand command) {
        if (command == null || StringUtils.isBlank(command.getAgentKey())) {
            return false;
        }
        return subAgentDefinitionDao.insert(toPo(command)) > 0;
    }

    @Override
    public boolean updateByAgentKey(SubAgentDefinitionUpsertCommand command) {
        if (command == null || StringUtils.isBlank(command.getAgentKey())) {
            return false;
        }
        return subAgentDefinitionDao.updateByAgentKey(toPo(command)) > 0;
    }

    @Override
    public boolean softDeleteByAgentKey(String agentKey) {
        if (StringUtils.isBlank(agentKey)) {
            return false;
        }
        return subAgentDefinitionDao.softDeleteByAgentKey(agentKey.trim()) > 0;
    }

    private SubAgentDefinition toDefinition(SubAgentDefinitionPO po) {
        SubAgentDefinitionRecord record = toRecord(po);
        if (record == null) {
            return null;
        }
        return SubAgentDefinition.builder()
                .agentType(record.getAgentKey())
                .whenToUse(record.getWhenToUse())
                .systemPrompt(record.getSystemPrompt())
                .allowedTools(record.getAllowedTools())
                .disallowedTools(record.getDisallowedTools())
                .maxSteps(record.getMaxSteps())
                .build();
    }

    private SubAgentDefinitionRecord toRecord(SubAgentDefinitionPO po) {
        if (po == null || StringUtils.isBlank(po.getAgentKey())) {
            return null;
        }
        String whenToUse = StringUtils.defaultIfBlank(po.getWhenToUse(), po.getDisplayName());
        if (StringUtils.isBlank(whenToUse)) {
            whenToUse = po.getAgentKey();
        }
        return SubAgentDefinitionRecord.builder()
                .agentKey(po.getAgentKey().trim())
                .displayName(po.getDisplayName())
                .whenToUse(whenToUse)
                .systemPrompt(StringUtils.defaultString(po.getSystemPrompt()))
                .allowedTools(parseToolSet(po.getAllowedToolsJson()))
                .disallowedTools(parseToolSet(po.getDisallowedToolsJson()))
                .maxSteps(po.getMaxSteps())
                .status(po.getStatus())
                .build();
    }

    private static SubAgentDefinitionPO toPo(SubAgentDefinitionUpsertCommand command) {
        Integer status = command.getStatus() == null ? 1 : command.getStatus();
        return SubAgentDefinitionPO.builder()
                .agentKey(command.getAgentKey().trim())
                .displayName(command.getDisplayName())
                .whenToUse(command.getWhenToUse())
                .systemPrompt(command.getSystemPrompt())
                .allowedToolsJson(toToolJson(command.getAllowedTools()))
                .disallowedToolsJson(toToolJson(command.getDisallowedTools()))
                .maxSteps(command.getMaxSteps())
                .status(status)
                .deleted(0)
                .build();
    }

    private static String toToolJson(Set<String> tools) {
        if (tools == null || tools.isEmpty()) {
            return null;
        }
        return JSON.toJSONString(tools);
    }

    private static Set<String> parseToolSet(String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            List<String> list = JSON.parseObject(json, new TypeReference<List<String>>() {
            });
            if (list == null || list.isEmpty()) {
                return null;
            }
            Set<String> set = new LinkedHashSet<>();
            for (String item : list) {
                if (StringUtils.isNotBlank(item)) {
                    set.add(item.trim());
                }
            }
            return set.isEmpty() ? null : set;
        } catch (Exception e) {
            log.warn("parse tool set json failed: {}", json, e);
            return Collections.emptySet();
        }
    }
}
