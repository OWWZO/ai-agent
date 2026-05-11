package org.wwz.ai.test.spring.ai;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;
import org.wwz.ai.domain.agent.runtime.tool.common.skill.SkillTool;
import org.wwz.ai.domain.agent.runtime.tool.skill.DefaultSkillRegistry;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillMarkdownParser;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillPathGuard;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRuntimeOptions;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillScriptDiscoverer;

import java.util.Collections;
import java.util.List;

/**
 * skill_tool 结果与描述相关测试。
 */
public class SkillToolTest {

    private DefaultSkillRegistry skillRegistry;

    @Before
    public void setUp() throws Exception {
        SkillPathGuard skillPathGuard = new SkillPathGuard();
        skillRegistry = new DefaultSkillRegistry(
                SkillRuntimeOptions.builder()
                        .enabled(true)
                        .directories(List.of(new ClassPathResource("skills").getFile().getAbsolutePath()))
                        .build(),
                new SkillMarkdownParser(),
                new SkillScriptDiscoverer(skillPathGuard),
                skillPathGuard
        );
        skillRegistry.refresh();
    }

    @Test
    public void shouldDescribeAvailableSkills() {
        SkillTool skillTool = new SkillTool(skillRegistry);

        String description = skillTool.getDescription();

        Assert.assertTrue(description.contains("skill 读取工具"));
        Assert.assertTrue(description.contains("sql-analysis"));
        Assert.assertTrue(description.contains("读取 SQL 指标说明并执行汇总脚本"));
    }

    @Test
    public void shouldReturnSkillContentAndScriptSummary() {
        SkillTool skillTool = new SkillTool(skillRegistry);

        String result = String.valueOf(skillTool.execute(Collections.singletonMap("skill_name", "sql-analysis")));

        Assert.assertTrue(result.contains("技能名称：sql-analysis"));
        Assert.assertTrue(result.contains("技能目录："));
        Assert.assertTrue(result.contains("scripts/summarize.py"));
        Assert.assertTrue(result.contains("# SQL Analysis"));
    }

    @Test
    public void shouldReturnExplicitErrorWhenSkillMissing() {
        SkillTool skillTool = new SkillTool(skillRegistry);

        String result = String.valueOf(skillTool.execute(Collections.singletonMap("skill_name", "missing-skill")));

        Assert.assertEquals("Skill not found: missing-skill", result);
    }
}
