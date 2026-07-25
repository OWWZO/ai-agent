package org.wwz.ai.domain.agent.adapter.repository;

import org.wwz.ai.domain.agent.runtime.subagent.SubAgentDefinition;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentDefinitionRecord;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentDefinitionUpsertCommand;

import java.util.List;
import java.util.Optional;

/**
 * 可配置子 Agent 定义仓储（装配配置，非 Execution Ledger）。
 */
public interface ISubAgentDefinitionRepository {

    /**
     * 查询全部启用且未删除的子 Agent 定义（运行时 Registry 加载）。
     */
    List<SubAgentDefinition> listEnabled();

    /**
     * 管理端列表（未删除；含禁用）。
     */
    List<SubAgentDefinitionRecord> listAll();

    Optional<SubAgentDefinitionRecord> findByAgentKey(String agentKey);

    boolean insert(SubAgentDefinitionUpsertCommand command);

    boolean updateByAgentKey(SubAgentDefinitionUpsertCommand command);

    boolean softDeleteByAgentKey(String agentKey);
}
