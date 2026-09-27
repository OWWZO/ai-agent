package org.wwz.ai.test.spring.ai;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentDefinition;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentToolFilter;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.tool.common.mcp.McpToolNames;
import org.wwz.ai.domain.agent.runtime.tool.deferred.DeferredToolCatalog;
import org.wwz.ai.domain.agent.runtime.tool.deferred.DeferredToolEntry;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class SubAgentDeferredToolPolicyTest {

    @Test
    public void customChildMovesConfiguredLocalToolToItsOwnCatalog() {
        ToolCollection parent = new ToolCollection();
        parent.addTool(new NamedTool("document_generate"));
        parent.addTool(new NamedTool("workspace_read"));

        ToolCollection child = SubAgentToolFilter.filter(parent, SubAgentDefinition.builder()
                .agentType("data-analyst")
                .toolPolicyMode(SubAgentDefinition.TOOL_POLICY_CUSTOM)
                .allowedTools(Set.of("document_generate", "workspace_read"))
                .deferredTools(Set.of("document_generate"))
                .build());

        Assert.assertFalse(child.getToolMap().containsKey("document_generate"));
        Assert.assertTrue(child.getToolMap().containsKey("workspace_read"));
        Assert.assertTrue(child.getToolMap().containsKey("ToolSearch"));
        Assert.assertTrue(child.getDeferredToolCatalog().contains("document_generate"));
        Assert.assertEquals("reactor", child.getDeferredToolCatalog()
                .get("document_generate").getSourceName());
    }

    @Test
    public void inheritedChildKeepsParentDeferredView() {
        ToolCollection parent = new ToolCollection();
        parent.setDeferredToolCatalog(new DeferredToolCatalog(List.of(
                DeferredToolEntry.local(new NamedTool("document_generate"), "reactor",
                        Map.of("type", "object"), ""))));
        ToolCollection child = SubAgentToolFilter.filter(parent, SubAgentDefinition.builder()
                .agentType(SubAgentDefinition.TOOL_POLICY_INHERIT)
                .allowedTools(Set.of("*"))
                .build());

        Assert.assertTrue(child.getDeferredToolCatalog().contains("document_generate"));
        Assert.assertTrue(child.getToolMap().containsKey("ToolCall"));
    }

    @Test
    public void toolCallWhitelistRetainsParentCatalogWithoutEagerExposure() {
        ToolCollection parent = new ToolCollection();
        parent.setDeferredToolCatalog(new DeferredToolCatalog(List.of(
                DeferredToolEntry.local(new NamedTool("document_generate"), "reactor",
                        Map.of("type", "object"), ""))));

        ToolCollection child = SubAgentToolFilter.filter(parent, SubAgentDefinition.builder()
                .agentType("bridge-reader")
                .toolPolicyMode(SubAgentDefinition.TOOL_POLICY_CUSTOM)
                .allowedTools(Set.of(McpToolNames.TOOL_CALL))
                .build());

        Assert.assertTrue(child.getDeferredToolCatalog().contains("document_generate"));
        Assert.assertFalse(child.getToolMap().containsKey("document_generate"));
        Assert.assertTrue(child.getToolMap().containsKey(McpToolNames.TOOL_CALL));
    }

    private static final class NamedTool implements BaseTool {
        private final String name;

        private NamedTool(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return name;
        }

        @Override
        public Map<String, Object> toParams() {
            return Map.of("type", "object");
        }

        @Override
        public Object execute(Object input) {
            return name;
        }
    }
}
