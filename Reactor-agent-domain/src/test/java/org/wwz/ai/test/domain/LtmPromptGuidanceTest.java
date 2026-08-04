package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.memory.ltm.LtmAgentForkSupport;
import org.wwz.ai.domain.agent.memory.ltm.LtmPromptGuidance;
import org.wwz.ai.domain.agent.runtime.tool.common.MemoryTool;

public class LtmPromptGuidanceTest {

    @Test
    public void memoryGuidanceMatchesHermesIntent() {
        Assert.assertTrue(LtmPromptGuidance.MEMORY_GUIDANCE.contains("persistent memory"));
        Assert.assertTrue(LtmPromptGuidance.MEMORY_GUIDANCE.contains("reduces future user steering")
                || LtmPromptGuidance.MEMORY_GUIDANCE.contains("stops the user repeating"));
        Assert.assertTrue(LtmPromptGuidance.MEMORY_GUIDANCE.contains("session_search"));
        Assert.assertTrue(LtmPromptGuidance.MEMORY_GUIDANCE.contains("declarative")
                || LtmPromptGuidance.MEMORY_GUIDANCE.contains("User prefers concise"));
        Assert.assertTrue(LtmPromptGuidance.MEMORY_GUIDANCE.contains("target=user"));
        Assert.assertTrue(LtmPromptGuidance.MEMORY_GUIDANCE.contains("target=curated"));
    }

    @Test
    public void forLoadedToolsGatesByToolPresence() {
        Assert.assertEquals("", LtmPromptGuidance.forLoadedTools(false, false));
        String memoryOnly = LtmPromptGuidance.forLoadedTools(true, false);
        Assert.assertTrue(memoryOnly.contains("persistent memory"));
        Assert.assertFalse(memoryOnly.contains(LtmPromptGuidance.SESSION_SEARCH_GUIDANCE));
        String both = LtmPromptGuidance.forLoadedTools(true, true);
        Assert.assertTrue(both.contains("persistent memory"));
        Assert.assertTrue(both.contains(LtmPromptGuidance.SESSION_SEARCH_GUIDANCE));
    }

    @Test
    public void writeStandardsSharedAcrossToolAndForks() {
        MemoryTool tool = new MemoryTool();
        String desc = tool.getDescription();
        Assert.assertEquals(LtmPromptGuidance.MEMORY_TOOL_DESCRIPTION, desc);
        Assert.assertTrue(desc.contains("save proactively"));
        Assert.assertTrue(desc.contains(LtmPromptGuidance.PRIORITY));
        Assert.assertTrue(desc.contains(LtmPromptGuidance.SKIP));
        Assert.assertTrue(desc.contains(LtmPromptGuidance.STYLE));

        Assert.assertEquals(LtmPromptGuidance.REVIEW_DIRECTIVE, LtmAgentForkSupport.REVIEW_DIRECTIVE);
        Assert.assertEquals(LtmPromptGuidance.FLUSH_DIRECTIVE, LtmAgentForkSupport.FLUSH_DIRECTIVE);

        Assert.assertTrue(LtmPromptGuidance.REVIEW_DIRECTIVE.contains("persona"));
        Assert.assertTrue(LtmPromptGuidance.REVIEW_DIRECTIVE.contains("Nothing to save"));
        Assert.assertTrue(LtmPromptGuidance.REVIEW_DIRECTIVE.contains(LtmPromptGuidance.SKIP));
        Assert.assertTrue(LtmPromptGuidance.FLUSH_DIRECTIVE.contains("about to be compacted"));
        Assert.assertTrue(LtmPromptGuidance.FLUSH_DIRECTIVE.contains(LtmPromptGuidance.PRIORITY));
    }

    @Test
    public void forkSystemPromptAlwaysIncludesWriteStandards() {
        String alone = LtmPromptGuidance.forkSystemPrompt(null);
        Assert.assertTrue(alone.contains("ONLY use the memory tool"));
        Assert.assertTrue(alone.contains("persistent memory"));

        String withParent = LtmPromptGuidance.forkSystemPrompt("You are helpful.\n");
        Assert.assertTrue(withParent.startsWith("You are helpful."));
        Assert.assertTrue(withParent.contains("# LTM fork directive"));
        Assert.assertTrue(withParent.contains(LtmPromptGuidance.MEMORY_GUIDANCE));
    }
}
