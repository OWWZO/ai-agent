package org.wwz.ai.test.domain.mcp;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.tool.mcp.model.McpResourceInfo;
import org.wwz.ai.domain.agent.runtime.tool.mcp.model.McpToolInfo;
import org.wwz.ai.domain.agent.runtime.tool.mcp.port.McpToolExecutor;

import java.util.List;
import java.util.Map;

/**
 * MCP 领域端口契约测试：只用 fake，不把 SDK 或基础设施类型带入 domain 测试。
 */
public class McpToolExecutorPortContractTest {

    @Test
    public void shouldRouteTypedToolCallsThroughPort() {
        FakeMcpToolExecutor executor = new FakeMcpToolExecutor();
        McpToolInfo tool = McpToolInfo.builder()
                .mcpId("mcp-1")
                .serverKey("demo")
                .name("mcp__demo__search")
                .originalName("search")
                .parameters("{}")
                .build();
        Assert.assertEquals("called:mcp-1:search", executor.executeTool(tool, Map.of("q", "value")));
        Assert.assertEquals("mcp-1", executor.lastMcpId);
        Assert.assertEquals("search", executor.lastToolName);
        Assert.assertEquals(Map.of("q", "value"), executor.lastArguments);
    }

    @Test
    public void shouldKeepDiscoveryAndResourceAliasesOnPort() {
        FakeMcpToolExecutor executor = new FakeMcpToolExecutor();

        Assert.assertEquals(executor.tools, executor.discoverConfiguredTools());
        Assert.assertEquals(executor.tools, executor.discoverTools(List.of("mcp-1")));
        Assert.assertEquals(executor.resources, executor.listGlobalResources());
        Assert.assertEquals("resource:mcp-1:file://demo", executor.readResource("mcp-1", "file://demo"));
        Assert.assertTrue(executor.hasAnyResources());
    }

    private static final class FakeMcpToolExecutor implements McpToolExecutor {
        private final McpToolInfo tool = McpToolInfo.builder()
                .mcpId("mcp-1")
                .serverKey("demo")
                .name("mcp__demo__search")
                .originalName("search")
                .parameters("{}")
                .build();
        private final List<McpToolInfo> tools = List.of(tool);
        private final List<McpResourceInfo> resources = List.of(McpResourceInfo.builder()
                .mcpId("mcp-1")
                .serverKey("demo")
                .uri("file://demo")
                .name("demo")
                .build());
        private String lastMcpId;
        private String lastToolName;
        private Object lastArguments;

        @Override
        public List<McpToolInfo> listGlobalEnabledTools() {
            return tools;
        }

        @Override
        public List<McpToolInfo> listToolsByMcpIds(List<String> mcpIds) {
            return tools;
        }

        @Override
        public List<McpResourceInfo> listGlobalEnabledResources() {
            return resources;
        }

        @Override
        public List<McpResourceInfo> listResourcesByMcpIds(List<String> mcpIds) {
            return resources;
        }

        @Override
        public List<McpResourceInfo> listResources(String serverOrMcpId) {
            return resources;
        }

        @Override
        public boolean hasAnyResources() {
            return true;
        }

        @Override
        public String readResource(String serverOrMcpId, String uri) {
            return "resource:" + serverOrMcpId + ":" + uri;
        }

        @Override
        public String callTool(String mcpId, String toolName, Object args) {
            lastMcpId = mcpId;
            lastToolName = toolName;
            lastArguments = args;
            return "called:" + mcpId + ":" + toolName;
        }

        @Override
        public void reload() {
        }
    }
}
