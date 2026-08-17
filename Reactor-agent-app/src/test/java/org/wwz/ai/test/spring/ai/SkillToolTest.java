package org.wwz.ai.test.spring.ai;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.common.skill.SkillTool;
import org.wwz.ai.domain.agent.runtime.tool.skill.DefaultSkillRegistry;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillMarkdownParser;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillPathGuard;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRuntimeLayout;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRuntimeOptions;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillScriptDiscoverer;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class SkillToolTest {

    private DefaultSkillRegistry skillRegistry;
    private SkillRuntimeLayout layout;

    @Before
    public void setUp() throws Exception {
        SkillPathGuard skillPathGuard = new SkillPathGuard();
        SkillRuntimeOptions options = SkillRuntimeOptions.builder()
                .enabled(true)
                .directories(List.of(new ClassPathResource("skills").getFile().getAbsolutePath()))
                .runtimePython("python")
                .build();
        skillRegistry = new DefaultSkillRegistry(
                options,
                new SkillMarkdownParser(),
                new SkillScriptDiscoverer(skillPathGuard),
                skillPathGuard
        );
        skillRegistry.refresh();
        layout = new SkillRuntimeLayout(options);
    }

    @Test
    public void shouldDescribeAvailableSkills() {
        SkillTool skillTool = new SkillTool(skillRegistry, layout);
        String description = skillTool.getDescription();
        Assert.assertTrue(description.contains("skill"));
        Assert.assertTrue(description.contains("sql-analysis"));
    }

    @Test
    public void shouldReturnSkillContentWithVirtualDir() {
        SkillTool skillTool = new SkillTool(skillRegistry, layout);
        ToolResultPayload payload = (ToolResultPayload) skillTool.execute(
                Collections.singletonMap("skill_name", "sql-analysis"));
        Assert.assertFalse(Boolean.TRUE.equals(payload.getFailed()));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) payload.getLlmData();
        Assert.assertEquals("sql-analysis", data.get("name"));
        Assert.assertEquals("skills/sql-analysis", data.get("skillDir"));
        Assert.assertTrue(String.valueOf(data.get("content")).contains("# SQL Analysis"));
    }

    @Test
    public void shouldReturnExplicitErrorWhenSkillMissing() {
        SkillTool skillTool = new SkillTool(skillRegistry, layout);
        ToolResultPayload payload = (ToolResultPayload) skillTool.execute(
                Collections.singletonMap("skill_name", "missing-skill"));
        Assert.assertTrue(Boolean.TRUE.equals(payload.getFailed()));
        Assert.assertEquals("Skill not found: missing-skill", payload.getErrorMsg());
    }
}
