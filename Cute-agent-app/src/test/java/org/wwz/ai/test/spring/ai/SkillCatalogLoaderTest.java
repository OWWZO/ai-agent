package org.wwz.ai.test.spring.ai;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.agent.BaseAgent;
import org.wwz.ai.domain.agent.runtime.llm.SessionPromptFreeze;
import org.wwz.ai.domain.agent.runtime.prompt.AgentPrompt;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.common.skill.SkillViewTool;
import org.wwz.ai.domain.agent.runtime.tool.common.skill.SkillsSearchTool;
import org.wwz.ai.domain.agent.runtime.tool.skill.DefaultSkillLoader;
import org.wwz.ai.domain.agent.runtime.tool.skill.DefaultSkillRegistry;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillDescriptor;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillDocument;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillFile;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillMarkdownParser;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillPathGuard;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillPromptIndexBuilder;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillQuery;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRuntimeLayout;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRuntimeOptions;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillScriptDiscoverer;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillSearchHit;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class SkillCatalogLoaderTest {

    @Test
    public void refreshDoesNotKeepSkillBody() throws Exception {
        Path root = Files.createTempDirectory("skill-catalog-");
        writeSkill(root.resolve("sql-analysis"), "sql-analysis",
                "读取 SQL 指标说明并生成结构化分析。",
                "data-analysis",
                List.of("sql", "metrics"),
                "# SQL Analysis\nsecret-body\n");
        Files.createDirectories(root.resolve("sql-analysis/references"));
        Files.writeString(root.resolve("sql-analysis/references/metrics.md"), "# Metrics\n", StandardCharsets.UTF_8);

        DefaultSkillRegistry catalog = registry(root);
        catalog.refresh();

        SkillDescriptor descriptor = catalog.getRequired("sql-analysis");
        Assert.assertNull(catalog.getRequiredSkill("sql-analysis").getContent());
        Assert.assertEquals("data-analysis", descriptor.getCategory());
        Assert.assertTrue(descriptor.getTags().contains("sql"));
        Assert.assertTrue(descriptor.getFiles().stream().anyMatch(file -> file.path().equals("references/metrics.md")));
        Assert.assertFalse(SkillPromptIndexBuilder.build(catalog.list()).contains("secret-body"));
    }

    @Test
    public void loaderReadsBodyAndLinkedFile() throws Exception {
        Path root = Files.createTempDirectory("skill-loader-");
        writeSkill(root.resolve("sql-analysis"), "sql-analysis",
                "读取 SQL 指标说明并生成结构化分析。",
                "data-analysis",
                List.of("sql"),
                "# SQL Analysis\nmain-body\n");
        Files.createDirectories(root.resolve("sql-analysis/references"));
        Files.writeString(root.resolve("sql-analysis/references/metrics.md"),
                "# Metrics\n- sales_amount\n", StandardCharsets.UTF_8);

        DefaultSkillRegistry catalog = registry(root);
        catalog.refresh();
        DefaultSkillLoader loader = new DefaultSkillLoader(catalog, new SkillMarkdownParser(), new SkillPathGuard());

        SkillDocument document = loader.load(catalog.resolve("sql-analysis"));
        Assert.assertTrue(document.getContent().contains("main-body"));

        Files.writeString(root.resolve("sql-analysis/SKILL.md"), """
                ---
                name: sql-analysis
                description: 读取 SQL 指标说明并生成结构化分析。
                category: data-analysis
                ---

                # SQL Analysis
                updated-body
                """, StandardCharsets.UTF_8);
        Assert.assertTrue(loader.load(catalog.resolve("sql-analysis")).getContent().contains("updated-body"));

        SkillFile file = loader.loadFile(catalog.resolve("sql-analysis"), "references/metrics.md");
        Assert.assertTrue(file.getContent().contains("sales_amount"));
    }

    @Test
    public void searchPrefersExactNameAndHonorsLimit() throws Exception {
        Path root = Files.createTempDirectory("skill-search-");
        writeSkill(root.resolve("sql-analysis"), "sql-analysis",
                "读取 SQL 指标说明并生成结构化分析。", "data-analysis", List.of("sql"), "# A\n");
        writeSkill(root.resolve("sales-analysis"), "sales-analysis",
                "分析销售数据并输出趋势和异常。", "data-analysis", List.of("sales"), "# B\n");
        writeSkill(root.resolve("pptx"), "pptx",
                "创建和修改 PPT。", "general", List.of(), "# C\n");

        DefaultSkillRegistry catalog = registry(root);
        catalog.refresh();

        List<SkillSearchHit> exact = catalog.search(SkillQuery.builder().text("sql-analysis").limit(10).build());
        Assert.assertEquals("sql-analysis", exact.get(0).descriptor().getName());

        List<SkillSearchHit> keyword = catalog.search(SkillQuery.builder().text("销售 趋势").limit(10).build());
        Assert.assertEquals("sales-analysis", keyword.get(0).descriptor().getName());

        List<SkillSearchHit> filtered = catalog.search(SkillQuery.builder()
                .text("分析")
                .category("data-analysis")
                .limit(1)
                .build());
        Assert.assertEquals(1, filtered.size());
        Assert.assertEquals("data-analysis", filtered.get(0).descriptor().getCategory());
    }

    @Test
    public void promptIndexStaysShortAndInjectsIntoSystem() throws Exception {
        Path root = Files.createTempDirectory("skill-prompt-");
        writeSkill(root.resolve("sql-analysis"), "sql-analysis",
                "读取 SQL 指标说明并生成结构化分析。", "data-analysis", List.of("sql"),
                "# SQL Analysis\nTHIS_BODY_MUST_NOT_ENTER_PROMPT\n");
        DefaultSkillRegistry catalog = registry(root);
        catalog.refresh();
        SkillPathGuard guard = new SkillPathGuard();
        SkillViewTool viewTool = new SkillViewTool(
                catalog,
                new DefaultSkillLoader(catalog, new SkillMarkdownParser(), guard),
                new SkillRuntimeLayout(SkillRuntimeOptions.builder().build()),
                new SkillScriptDiscoverer(guard));

        String index = SkillPromptIndexBuilder.build(catalog.list());
        Assert.assertTrue(index.contains(SkillPromptIndexBuilder.AVAILABLE_SKILLS_OPEN));
        Assert.assertTrue(index.contains("sql-analysis"));
        Assert.assertFalse(index.contains("THIS_BODY_MUST_NOT_ENTER_PROMPT"));
        Assert.assertTrue(viewTool.getDescription().length() < 200);
        Assert.assertFalse(viewTool.getDescription().contains("sql-analysis"));

        ToolCollection tools = new ToolCollection();
        tools.addTool(viewTool);
        tools.addTool(new SkillsSearchTool(catalog));
        String sessionId = "skill-prompt-session";
        SessionPromptFreeze.clearSession(sessionId);
        ProbeAgent agent = new ProbeAgent();
        agent.setName("react");
        agent.setContext(AgentContext.builder()
                .sessionId(sessionId)
                .toolCollection(tools)
                .build());
        String system = agent.expose(AgentPrompt.REACT_SYSTEM_PROMPT);
        Assert.assertTrue(system.contains("<available_skills>"));
        Assert.assertTrue(system.contains("skill_view"));
        Assert.assertFalse(system.contains("THIS_BODY_MUST_NOT_ENTER_PROMPT"));

        writeSkill(root.resolve("pptx"), "pptx", "创建 PPT。", "general", List.of(), "# PPT\n");
        catalog.refresh();
        String frozen = agent.expose(AgentPrompt.REACT_SYSTEM_PROMPT);
        Assert.assertEquals(system, frozen);
        Assert.assertFalse(frozen.contains("pptx:"));
        SessionPromptFreeze.clearSession(sessionId);
        String reloaded = agent.expose(AgentPrompt.REACT_SYSTEM_PROMPT);
        Assert.assertTrue(reloaded.contains("pptx"));
    }

    @Test
    public void toolsSearchAndViewWorkTogether() throws Exception {
        Path root = Files.createTempDirectory("skill-tools-");
        writeSkill(root.resolve("sql-analysis"), "sql-analysis",
                "读取 SQL 指标说明并生成结构化分析。", "data-analysis", List.of("sql"), "# SQL Analysis\n");
        DefaultSkillRegistry catalog = registry(root);
        catalog.refresh();
        SkillPathGuard guard = new SkillPathGuard();
        SkillViewTool viewTool = new SkillViewTool(
                catalog,
                new DefaultSkillLoader(catalog, new SkillMarkdownParser(), guard),
                new SkillRuntimeLayout(SkillRuntimeOptions.builder().build()),
                new SkillScriptDiscoverer(guard));
        SkillsSearchTool searchTool = new SkillsSearchTool(catalog);

        ToolResultPayload search = (ToolResultPayload) searchTool.execute(Map.of("query", "SQL 指标"));
        Assert.assertFalse(Boolean.TRUE.equals(search.getFailed()));
        Assert.assertTrue(String.valueOf(search.getLlmData()).contains("sql-analysis"));

        ToolResultPayload view = (ToolResultPayload) viewTool.execute(Map.of("name", "sql-analysis"));
        Assert.assertFalse(Boolean.TRUE.equals(view.getFailed()));
        Assert.assertTrue(String.valueOf(view.getLlmData()).contains("# SQL Analysis"));
    }

    private static DefaultSkillRegistry registry(Path root) {
        SkillPathGuard guard = new SkillPathGuard();
        return new DefaultSkillRegistry(
                SkillRuntimeOptions.builder().enabled(true).directories(List.of(root.toString())).build(),
                new SkillMarkdownParser(),
                new SkillScriptDiscoverer(guard),
                guard);
    }

    private static void writeSkill(Path directory, String name, String description,
                                   String category, List<String> tags, String body) throws Exception {
        Files.createDirectories(directory);
        String tagLine = tags == null || tags.isEmpty() ? "" : "tags: [" + String.join(", ", tags) + "]\n";
        String markdown = """
                ---
                name: %s
                description: %s
                category: %s
                %s---

                %s
                """.formatted(name, description, category, tagLine, body);
        Files.writeString(directory.resolve("SKILL.md"), markdown, StandardCharsets.UTF_8);
    }

    private static final class ProbeAgent extends BaseAgent {
        @Override
        public String step() {
            return "";
        }

        String expose(String template) {
            return buildStableSystemPrompt(AgentPrompt.ensureUserFacingReplyContract(template));
        }
    }
}
