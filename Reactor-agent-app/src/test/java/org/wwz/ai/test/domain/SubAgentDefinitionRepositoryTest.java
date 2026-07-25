package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentDefinition;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentDefinitionLoader;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentRegistry;
import org.wwz.ai.infrastructure.adapter.repository.SubAgentDefinitionRepository;
import org.wwz.ai.infrastructure.dao.ISubAgentDefinitionDao;
import org.wwz.ai.infrastructure.dao.po.SubAgentDefinitionPO;

import java.util.List;

/**
 * SubAgentDefinitionRepository JSON 解析与映射。
 */
public class SubAgentDefinitionRepositoryTest {

    @Test
    public void shouldMapEnabledRowsToDefinitions() {
        InMemoryDao dao = new InMemoryDao();
        dao.rows = List.of(
                SubAgentDefinitionPO.builder()
                        .agentKey("code-reviewer")
                        .displayName("代码审查")
                        .whenToUse("只读审查")
                        .systemPrompt("review prompt")
                        .allowedToolsJson("[\"workspace_read\",\"workspace_grep\"]")
                        .disallowedToolsJson("[\"workspace_write\"]")
                        .maxSteps(10)
                        .status(1)
                        .deleted(0)
                        .build()
        );
        SubAgentDefinitionRepository repository = new SubAgentDefinitionRepository(dao);

        List<SubAgentDefinition> list = repository.listEnabled();

        Assert.assertEquals(1, list.size());
        SubAgentDefinition def = list.get(0);
        Assert.assertEquals("code-reviewer", def.getAgentType());
        Assert.assertEquals("只读审查", def.getWhenToUse());
        Assert.assertTrue(def.getAllowedTools().contains("workspace_read"));
        Assert.assertTrue(def.getDisallowedTools().contains("workspace_write"));
        Assert.assertEquals(Integer.valueOf(10), def.getMaxSteps());
    }

    @Test
    public void blankAllowedToolsJsonMeansAllowAllSemantics() {
        InMemoryDao dao = new InMemoryDao();
        dao.rows = List.of(
                SubAgentDefinitionPO.builder()
                        .agentKey("general-custom")
                        .whenToUse("通用")
                        .systemPrompt("sys")
                        .allowedToolsJson(null)
                        .disallowedToolsJson("[]")
                        .status(1)
                        .deleted(0)
                        .build()
        );
        SubAgentDefinitionRepository repository = new SubAgentDefinitionRepository(dao);

        SubAgentDefinition def = repository.listEnabled().get(0);
        Assert.assertTrue(def.allowsAllTools());
    }

    @Test
    public void loaderShouldRegisterRepositoryDefinitions() {
        InMemoryDao dao = new InMemoryDao();
        dao.rows = List.of(
                SubAgentDefinitionPO.builder()
                        .agentKey("research-writer")
                        .whenToUse("调研并写简报")
                        .systemPrompt("写简洁简报")
                        .allowedToolsJson("[\"deep_search\",\"web_fetch\"]")
                        .maxSteps(12)
                        .status(1)
                        .deleted(0)
                        .build()
        );
        SubAgentDefinitionRepository repository = new SubAgentDefinitionRepository(dao);
        SubAgentRegistry registry = new SubAgentRegistry();
        SubAgentDefinitionLoader loader = new SubAgentDefinitionLoader(registry, repository);

        int count = loader.reload();

        Assert.assertEquals(1, count);
        Assert.assertTrue(registry.find("research-writer").isPresent());
        Assert.assertEquals(12, registry.require("research-writer").getMaxSteps().intValue());
        Assert.assertTrue(registry.find(SubAgentRegistry.TYPE_EXPLORE).isPresent());
    }

    private static final class InMemoryDao implements ISubAgentDefinitionDao {
        private List<SubAgentDefinitionPO> rows = List.of();

        @Override
        public List<SubAgentDefinitionPO> queryEnabled() {
            return rows.stream()
                    .filter(r -> r.getStatus() != null && r.getStatus() == 1)
                    .filter(r -> r.getDeleted() == null || r.getDeleted() == 0)
                    .toList();
        }

        @Override
        public List<SubAgentDefinitionPO> queryAll() {
            return rows.stream()
                    .filter(r -> r.getDeleted() == null || r.getDeleted() == 0)
                    .toList();
        }

        @Override
        public SubAgentDefinitionPO queryByAgentKey(String agentKey) {
            return rows.stream()
                    .filter(r -> agentKey.equals(r.getAgentKey()))
                    .filter(r -> r.getDeleted() == null || r.getDeleted() == 0)
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public int insert(SubAgentDefinitionPO po) {
            return 1;
        }

        @Override
        public int updateByAgentKey(SubAgentDefinitionPO po) {
            return 1;
        }

        @Override
        public int softDeleteByAgentKey(String agentKey) {
            return 1;
        }
    }
}
