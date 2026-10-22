package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.tool.mcp.model.McpToolInfo;
import org.wwz.ai.domain.agent.runtime.tool.deferred.DeferredToolCatalog;
import org.wwz.ai.domain.agent.runtime.tool.deferred.DeferredToolEntry;

import java.util.List;
import java.util.Map;

/**
 * MCP entry 在通用 deferred catalog 中的 BM25 / select / listing 行为。
 */
public class DeferredToolCatalogMcpTest {

    @Test
    public void searchDocumentsHitsAlpha() {
        DeferredToolCatalog catalog = catalog(alpha(), beta());
        List<DeferredToolEntry> hits = catalog.search("documents", 5);
        Assert.assertEquals(1, hits.size());
        Assert.assertEquals("mcp__demo__alpha", hits.get(0).getName());
    }

    @Test
    public void selectExactQualifiedName() {
        DeferredToolCatalog catalog = catalog(alpha(), beta());
        List<DeferredToolEntry> hits = catalog.search("select:mcp__demo__beta", 5);
        Assert.assertEquals(1, hits.size());
        Assert.assertEquals("mcp__demo__beta", hits.get(0).getName());
    }

    @Test
    public void bm25RanksCreateGithubIssueFirst() {
        McpToolInfo createIssue = McpToolInfo.builder()
                .name("mcp__github__create_issue")
                .originalName("create_issue")
                .desc("Open a new issue in a GitHub repository.")
                .parameters("{\"type\":\"object\",\"properties\":{\"title\":{\"type\":\"string\"}}}")
                .serverKey("github")
                .build();
        McpToolInfo searchRepos = McpToolInfo.builder()
                .name("mcp__github__search_repos")
                .originalName("search_repos")
                .desc("Search GitHub repositories by keyword.")
                .parameters("{\"type\":\"object\",\"properties\":{\"q\":{\"type\":\"string\"}}}")
                .serverKey("github")
                .build();
        DeferredToolCatalog catalog = catalog(searchRepos, createIssue);
        List<DeferredToolEntry> hits = catalog.search("create github issue", 5);
        Assert.assertFalse(hits.isEmpty());
        Assert.assertEquals("mcp__github__create_issue", hits.get(0).getName());
    }

    @Test
    public void zeroHitReturnsEmptyAndAvailableSources() {
        DeferredToolCatalog catalog = catalog(alpha(), beta());
        Assert.assertTrue(catalog.search("zzzz-no-such-tool", 5).isEmpty());
        List<Map<String, Object>> sources = catalog.availableSources();
        Assert.assertFalse(sources.isEmpty());
        Assert.assertEquals("demo", sources.get(0).get("name"));
        Assert.assertEquals(2, sources.get(0).get("tool_count"));
    }

    @Test
    public void emptyQueryDoesNotPreviewAll() {
        DeferredToolCatalog catalog = catalog(alpha(), beta());
        Assert.assertTrue(catalog.search("", 5).isEmpty());
        Assert.assertTrue(catalog.search("   ", 5).isEmpty());
    }

    @Test
    public void listingIsGroupedSortedAndStable() {
        DeferredToolCatalog first = catalog(beta(), alpha(), githubIssue());
        DeferredToolCatalog second = catalog(githubIssue(), alpha(), beta());
        String listing = first.formatListingForToolSearchDescription();
        Assert.assertEquals(listing, second.formatListingForToolSearchDescription());
        Assert.assertTrue(listing.contains("demo MCP tools (2):"));
        Assert.assertTrue(listing.indexOf("mcp__demo__alpha") < listing.indexOf("mcp__demo__beta"));
        Assert.assertFalse(listing.contains("properties"));
        Assert.assertFalse(listing.contains("<available-deferred-tools>"));
    }

    @Test
    public void filterDropsNamesAndDoesNotMutateOriginal() {
        DeferredToolCatalog catalog = catalog(alpha(), beta());
        DeferredToolCatalog filtered = catalog.filter(tool -> "mcp__demo__alpha".equals(tool.getName()));
        Assert.assertEquals(2, catalog.size());
        Assert.assertEquals(1, filtered.size());
        Assert.assertTrue(filtered.contains("mcp__demo__alpha"));
        Assert.assertFalse(filtered.contains("mcp__demo__beta"));
    }

    private static McpToolInfo alpha() {
        return McpToolInfo.builder()
                .name("mcp__demo__alpha")
                .originalName("alpha")
                .desc("search documents")
                .parameters("{\"type\":\"object\",\"properties\":{\"q\":{\"type\":\"string\"}}}")
                .serverKey("demo")
                .alwaysLoad(false)
                .build();
    }

    private static McpToolInfo beta() {
        return McpToolInfo.builder()
                .name("mcp__demo__beta")
                .originalName("beta")
                .desc("write file")
                .parameters("{}")
                .serverKey("demo")
                .build();
    }

    private static McpToolInfo githubIssue() {
        return McpToolInfo.builder()
                .name("mcp__github__create_issue")
                .originalName("create_issue")
                .desc("Open a new issue.")
                .serverKey("github")
                .build();
    }

    private static DeferredToolCatalog catalog(McpToolInfo... tools) {
        List<DeferredToolEntry> entries = new java.util.ArrayList<>();
        for (McpToolInfo tool : tools) {
            Map<String, Object> parameters = new java.util.LinkedHashMap<>();
            parameters.put("type", "object");
            parameters.put("properties", Map.of());
            entries.add(DeferredToolEntry.mcp(tool, tool.getServerKey(), parameters, tool.getSearchHint()));
        }
        return new DeferredToolCatalog(entries);
    }
}
