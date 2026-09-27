package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.dto.tool.McpToolInfo;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.deferred.DeferredToolCatalog;
import org.wwz.ai.domain.agent.runtime.tool.deferred.DeferredToolEntry;

import java.util.List;
import java.util.Map;

public class DeferredToolCatalogTest {

    @Test
    public void searchesLocalAndMcpWithTheSameCatalog() {
        DeferredToolCatalog catalog = new DeferredToolCatalog(List.of(
                localEntry(),
                DeferredToolEntry.mcp(McpToolInfo.builder()
                        .name("mcp__github__create_issue")
                        .originalName("create_issue")
                        .serverKey("github")
                        .desc("create a GitHub issue")
                        .parameters("{\"type\":\"object\",\"required\":[\"title\"]}")
                        .build(), "github", Map.of("type", "object"), "")));

        Assert.assertEquals("document_generate", catalog.search("documents", 5).get(0).getName());
        Assert.assertEquals("document_generate", catalog.search("select:document_generate", 5).get(0).getName());
        Assert.assertEquals("local", catalog.search("select:document_generate", 5).get(0).getSource().name().toLowerCase());
        Assert.assertEquals("github", catalog.search("select:mcp__github__create_issue", 5).get(0).getSourceName());
    }

    @Test
    public void listingGroupsLocalReactorAndMcpServer() {
        DeferredToolCatalog catalog = new DeferredToolCatalog(List.of(
                localEntry(),
                DeferredToolEntry.mcp(McpToolInfo.builder()
                        .name("mcp__github__create_issue")
                        .serverKey("github")
                        .desc("create issue")
                        .parameters("{}")
                        .build(), "github", Map.of("type", "object"), "")));

        String listing = catalog.formatListingForToolSearchDescription();
        Assert.assertTrue(listing.contains("reactor local tools"));
        Assert.assertTrue(listing.contains("github MCP tools"));
        Assert.assertFalse(listing.contains("properties"));
    }

    @Test
    public void catalogCollectionsAreImmutable() {
        DeferredToolCatalog catalog = new DeferredToolCatalog(List.of(
                localEntry()));
        Assert.assertThrows(UnsupportedOperationException.class, () -> catalog.listAll().clear());
        Assert.assertEquals(1, catalog.size());
    }

    private static final class LocalTool implements BaseTool {
        @Override
        public String getName() {
            return "document_generate";
        }

        @Override
        public String getDescription() {
            return "generate documents";
        }

        @Override
        public Map<String, Object> toParams() {
            return Map.of("type", "object", "properties", Map.of("title", Map.of("type", "string")));
        }

        @Override
        public Object execute(Object input) {
            return "ok";
        }
    }

    private static DeferredToolEntry localEntry() {
        return DeferredToolEntry.local(new LocalTool(), "reactor", Map.of(
                "type", "object",
                "properties", Map.of("title", Map.of("type", "string"))), "");
    }
}
