package org.wwz.ai.test.domain.subagent;
import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentDefinition;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentRegistry;

import java.util.List;
import java.util.Set;

/**
 * 可配置子 Agent：Registry 合并（不触达 @Slf4j 类，避免 domain 单测 logback 冲突）。
 */
public class SubAgentRegistryConfiguredTest {

    @Test
    public void shouldMergeConfiguredTypesAfterReplace() {
        SubAgentRegistry registry = new SubAgentRegistry();
        registry.replaceConfigured(List.of(
                SubAgentDefinition.builder()
                        .agentType("code-reviewer")
                        .whenToUse("只读代码审查")
                        .systemPrompt("你是代码审查子代理")
                        .allowedTools(Set.of("workspace_read", "workspace_grep"))
                        .disallowedTools(Set.of("workspace_write"))
                        .maxSteps(10)
                        .build()
        ));

        Assert.assertTrue(registry.find("code-reviewer").isPresent());
        Assert.assertFalse(registry.find("Explore").isPresent());
        Assert.assertEquals(1, registry.configuredCount());
        Assert.assertEquals("只读代码审查", registry.require("code-reviewer").getWhenToUse());
        Assert.assertTrue(registry.listTypeNames().contains("code-reviewer"));
    }

    @Test
    public void shouldNotOverrideBuiltinWithConfigured() {
        SubAgentRegistry registry = new SubAgentRegistry();
        registry.replaceConfigured(List.of(
                SubAgentDefinition.builder()
                        .agentType(SubAgentRegistry.TYPE_GENERAL_PURPOSE)
                        .whenToUse("恶意覆盖")
                        .systemPrompt("should not apply")
                        .allowedTools(Set.of("*"))
                        .build()
        ));

        Assert.assertEquals(0, registry.configuredCount());
        Assert.assertFalse(registry.require(SubAgentRegistry.TYPE_GENERAL_PURPOSE).getWhenToUse().contains("恶意"));
    }

    @Test
    public void listShouldExposeConfiguredWhenToUseForAgentToolDescription() {
        SubAgentRegistry registry = new SubAgentRegistry();
        registry.replaceConfigured(List.of(
                SubAgentDefinition.builder()
                        .agentType("code-reviewer")
                        .whenToUse("代码审查")
                        .systemPrompt("review")
                        .allowedTools(Set.of("workspace_read"))
                        .build()
        ));

        boolean listed = registry.list().stream()
                .anyMatch(d -> "code-reviewer".equals(d.getAgentType())
                        && "代码审查".equals(d.getWhenToUse()));
        Assert.assertTrue(listed);
        Assert.assertTrue(registry.listTypeNames().contains("code-reviewer"));
        Assert.assertFalse(registry.listTypeNames().contains("Explore"));
    }

    @Test
    public void replaceConfiguredShouldClearPrevious() {
        SubAgentRegistry registry = new SubAgentRegistry();
        registry.replaceConfigured(List.of(
                SubAgentDefinition.builder()
                        .agentType("a")
                        .whenToUse("a")
                        .systemPrompt("a")
                        .build()
        ));
        registry.replaceConfigured(List.of(
                SubAgentDefinition.builder()
                        .agentType("b")
                        .whenToUse("b")
                        .systemPrompt("b")
                        .build()
        ));

        Assert.assertFalse(registry.find("a").isPresent());
        Assert.assertTrue(registry.find("b").isPresent());
        Assert.assertEquals(1, registry.configuredCount());
    }
}
