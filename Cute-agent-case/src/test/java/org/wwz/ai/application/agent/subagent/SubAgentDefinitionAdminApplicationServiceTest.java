package org.wwz.ai.application.agent.subagent;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.adapter.repository.ISubAgentDefinitionRepository;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentDefinition;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentDefinitionLoader;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentDefinitionRecord;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentDefinitionUpsertCommand;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentRegistry;

import java.util.List;
import java.util.Optional;

/**
 * 子 Agent 管理端输入校验。
 */
public class SubAgentDefinitionAdminApplicationServiceTest {

    @Test
    public void shouldAcceptSlashInAgentKey() {
        InMemoryRepository repository = new InMemoryRepository();
        SubAgentDefinitionAdminApplicationService service = new SubAgentDefinitionAdminApplicationService(
                repository,
                new SubAgentDefinitionLoader(new SubAgentRegistry(), repository));

        boolean created = service.create(SubAgentDefinitionUpsertCommand.builder()
                .agentKey("team/reviewer")
                .whenToUse("审查代码")
                .systemPrompt("执行代码审查")
                .build());

        Assert.assertTrue(created);
    }

    private static final class InMemoryRepository implements ISubAgentDefinitionRepository {

        @Override
        public List<SubAgentDefinition> listEnabled() {
            return List.of();
        }

        @Override
        public List<SubAgentDefinitionRecord> listAll() {
            return List.of();
        }

        @Override
        public Optional<SubAgentDefinitionRecord> findByAgentKey(String agentKey) {
            return Optional.empty();
        }

        @Override
        public boolean insert(SubAgentDefinitionUpsertCommand command) {
            return true;
        }

        @Override
        public boolean updateByAgentKey(SubAgentDefinitionUpsertCommand command) {
            return true;
        }

        @Override
        public boolean softDeleteByAgentKey(String agentKey) {
            return true;
        }
    }
}
