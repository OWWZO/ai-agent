package org.wwz.ai.test.spring.ai;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.common.skill.SkillViewTool;
import org.wwz.ai.domain.agent.runtime.tool.skill.DefaultSkillLoader;
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
    private SkillViewTool skillViewTool;

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
        skillViewTool = new SkillViewTool(
                skillRegistry,
                new DefaultSkillLoader(skillRegistry, new SkillMarkdownParser(), skillPathGuard),
                layout,
                new SkillScriptDiscoverer(skillPathGuard));
    }

    @Test
    public void shouldUseFixedDescriptionWithoutCatalog() {
        String description = skillViewTool.getDescription();
        Assert.assertTrue(description.contains("skills_search"));
        Assert.assertFalse(description.contains("sql-analysis"));
    }

    @Test
    public void shouldReturnSkillContentWithVirtualDir() {
        ToolResultPayload payload = (ToolResultPayload) skillViewTool.execute(
                Collections.singletonMap("name", "sql-analysis"));
        Assert.assertFalse(Boolean.TRUE.equals(payload.getFailed()));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) payload.getLlmData();
        Assert.assertEquals("sql-analysis", data.get("name"));
        Assert.assertEquals("skills/sql-analysis", data.get("skillDir"));
        Assert.assertTrue(String.valueOf(data.get("content")).contains("# SQL Analysis"));
        Assert.assertTrue(String.valueOf(data.get("linkedFiles")).contains("references/metrics.md"));
    }

    @Test
    public void shouldLoadLinkedFile() {
        ToolResultPayload payload = (ToolResultPayload) skillViewTool.execute(Map.of(
                "name", "sql-analysis",
                "file_path", "references/metrics.md"));
        Assert.assertFalse(Boolean.TRUE.equals(payload.getFailed()));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) payload.getLlmData();
        Assert.assertTrue(String.valueOf(data.get("content")).contains("sales_amount"));
    }

    @Test
    public void shouldRejectPathTraversal() {
        ToolResultPayload payload = (ToolResultPayload) skillViewTool.execute(Map.of(
                "name", "sql-analysis",
                "file_path", "../outside.md"));
        Assert.assertTrue(Boolean.TRUE.equals(payload.getFailed()));
    }

    @Test
    public void shouldReturnAvailableFilesWhenMissing() {
        ToolResultPayload payload = (ToolResultPayload) skillViewTool.execute(Map.of(
                "name", "sql-analysis",
                "file_path", "references/missing.md"));
        Assert.assertTrue(Boolean.TRUE.equals(payload.getFailed()));
        @SuppressWarnings("unchecked")
        Map<String, Object> detail = (Map<String, Object>) payload.getLlmData();
        Assert.assertTrue(String.valueOf(detail.get("available_files")).contains("references/metrics.md"));
    }

    @Test
    public void shouldReturnExplicitErrorWhenSkillMissing() {
        ToolResultPayload payload = (ToolResultPayload) skillViewTool.execute(
                Collections.singletonMap("name", "missing-skill"));
        Assert.assertTrue(Boolean.TRUE.equals(payload.getFailed()));
        Assert.assertEquals("Skill not found: missing-skill", payload.getErrorMsg());
    }

}
