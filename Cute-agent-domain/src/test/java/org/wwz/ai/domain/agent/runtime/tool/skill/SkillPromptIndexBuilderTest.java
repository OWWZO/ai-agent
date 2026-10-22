package org.wwz.ai.domain.agent.runtime.tool.skill;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SkillPromptIndexBuilderTest {

    private static SkillDescriptor descriptor(String name, String description, String category) {
        return SkillDescriptor.builder()
                .name(name)
                .description(description)
                .category(category)
                .build();
    }

    private static List<SkillDescriptor> manySkills(int count, int categories) {
        List<SkillDescriptor> descriptors = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            descriptors.add(descriptor(
                    String.format("skill-%02d", i),
                    "描述".repeat(30),
                    "category-" + (i % categories)));
        }
        return descriptors;
    }

    @Test
    public void shouldRenderFullIndexWhenWithinBudget() {
        String index = SkillPromptIndexBuilder.build(
                List.of(descriptor("alpha", "a short description", "demo")),
                Set.of(),
                6000,
                80);

        Assert.assertTrue(index.startsWith("# Skills\n"));
        Assert.assertTrue(index.contains(SkillPromptIndexBuilder.AVAILABLE_SKILLS_OPEN));
        Assert.assertTrue(index.contains("  demo:\n"));
        Assert.assertTrue(index.contains("    - alpha: a short description\n"));
        Assert.assertFalse("未超预算不应出现降级提示", index.contains(SkillPromptIndexBuilder.DEGRADED_HINT));
    }

    @Test
    public void shouldKeepLegacyOutputShapeForSmallCatalog() {
        List<SkillDescriptor> descriptors = List.of(descriptor("alpha", "a short description", "demo"));

        Assert.assertEquals(
                SkillPromptIndexBuilder.build(descriptors, Set.of()),
                SkillPromptIndexBuilder.build(descriptors, Set.of(), SkillPromptIndexBuilder.DEFAULT_PROMPT_INDEX_MAX_CHARS, 80));
    }

    @Test
    public void shouldCompactDescriptionsBeforeDroppingEntries() {
        List<SkillDescriptor> descriptors = manySkills(20, 2);
        String full = SkillPromptIndexBuilder.build(descriptors, Set.of(), -1, 80);
        int budget = full.length() - 200;

        String index = SkillPromptIndexBuilder.build(descriptors, Set.of(), budget, 80);

        Assert.assertTrue("应出现降级提示", index.contains(SkillPromptIndexBuilder.DEGRADED_HINT));
        Assert.assertTrue(index.length() <= budget);
        Assert.assertTrue("第二档仍应保留全部 skill 名称", index.contains("skill-00"));
        Assert.assertTrue(index.contains("skill-19"));
    }

    @Test
    public void shouldDegradeCategoriesToSummaryWhenSeverelyOverBudget() {
        List<SkillDescriptor> descriptors = manySkills(30, 3);

        String index = SkillPromptIndexBuilder.build(descriptors, Set.of(), 400, 80);

        Assert.assertTrue(index.contains(SkillPromptIndexBuilder.DEGRADED_HINT));
        Assert.assertTrue("分类应降级为一行摘要", index.contains("个 skill（用 skills_search 查询）"));
        Assert.assertTrue(index.length() <= 400 + 40);
    }

    @Test
    public void shouldNeverLoseCategoryVisibility() {
        String index = SkillPromptIndexBuilder.build(manySkills(30, 3), Set.of(), 400, 80);

        for (String category : List.of("category-0", "category-1", "category-2")) {
            Assert.assertTrue("分类 " + category + " 应仍然可见", index.contains(category));
        }
    }

    @Test
    public void shouldFilterDisabledSkills() {
        List<SkillDescriptor> descriptors = List.of(
                descriptor("alpha", "a", "demo"),
                descriptor("beta", "b", "demo"));

        String index = SkillPromptIndexBuilder.build(descriptors, Set.of("beta"), 6000, 80);

        Assert.assertTrue(index.contains("alpha"));
        Assert.assertFalse(index.contains("beta"));
    }

    @Test
    public void shouldTreatNonPositiveBudgetAsUnlimited() {
        List<SkillDescriptor> descriptors = manySkills(30, 3);

        String index = SkillPromptIndexBuilder.build(descriptors, Set.of(), 0, 80);

        Assert.assertFalse(index.contains(SkillPromptIndexBuilder.DEGRADED_HINT));
    }

    @Test
    public void shouldRenderEmptyCatalog() {
        String index = SkillPromptIndexBuilder.build(List.of(), Set.of(), 6000, 80);

        Assert.assertTrue(index.contains("（当前没有可用 skill）"));
        Assert.assertTrue(index.contains(SkillPromptIndexBuilder.AVAILABLE_SKILLS_CLOSE));
    }
}
