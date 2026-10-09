package org.wwz.ai.application.catalog.mcp;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.application.catalog.ApplicationResult;
import org.wwz.ai.domain.agent.catalog.model.AiClientToolMcpConfig;
import org.wwz.ai.domain.agent.catalog.model.CatalogMcp;
import org.wwz.ai.domain.agent.catalog.model.McpRuntimeReloadResult;
import org.wwz.ai.domain.agent.catalog.port.IAiClientToolMcpConfigRepository;
import org.wwz.ai.domain.agent.catalog.port.IMcpRuntimeReloadPort;

import java.util.List;
import java.util.Optional;

public class McpCatalogApplicationServiceTest {

    @Test
    public void shouldExposeReloadFailureWithoutChangingBooleanResponseShape() {
        InMemoryRepository repository = new InMemoryRepository();
        McpCatalogApplicationService service = newService(repository,
                () -> McpRuntimeReloadResult.failed("client detail must stay internal"));

        ApplicationResult<Boolean> result = service.createMcp(command("catalog-mcp"));

        Assert.assertEquals("0000", result.code());
        Assert.assertTrue(result.data());
        Assert.assertEquals("MCP 配置已写入，但运行时刷新失败", result.info());
        Assert.assertNotNull(repository.inserted);
    }

    @Test
    public void shouldRejectDuplicateMcpIdBeforeInsert() {
        InMemoryRepository repository = new InMemoryRepository();
        repository.existing = AiClientToolMcpConfig.builder().mcpId("catalog-mcp").build();
        McpCatalogApplicationService service = newService(repository, McpRuntimeReloadResult::succeeded);

        ApplicationResult<Boolean> result = service.createMcp(command("catalog-mcp"));

        Assert.assertEquals("0002", result.code());
        Assert.assertFalse(result.data());
        Assert.assertNull(repository.inserted);
    }

    @Test
    public void shouldProjectCapabilitiesWithoutTransportConfig() {
        InMemoryRepository repository = new InMemoryRepository();
        repository.all = List.of(AiClientToolMcpConfig.builder()
                .mcpId("catalog-mcp")
                .mcpName("Public MCP")
                .transportType("streamable_http")
                .transportConfig("{\"apiKey\":\"secret\"}")
                .status(1)
                .build());
        McpCatalogApplicationService service = newService(repository, McpRuntimeReloadResult::succeeded);

        ApplicationResult<List<CatalogMcp>> result = service.listMcps();

        Assert.assertEquals("0000", result.code());
        CatalogMcp mcp = result.data().get(0);
        Assert.assertEquals("catalog-mcp", mcp.mcpId());
        Assert.assertEquals("Public MCP", mcp.mcpName());
        Assert.assertEquals("streamable_http", mcp.transportType());
        Assert.assertEquals(Integer.valueOf(1), mcp.status());
    }

    private static McpCatalogApplicationService newService(
            InMemoryRepository repository, IMcpRuntimeReloadPort reloadPort) {
        return new McpCatalogApplicationService(
                repository,
                reloadPort,
                new McpConfigurationApplicationService(repository));
    }

    private static CatalogMcpCreateCommand command(String mcpId) {
        return new CatalogMcpCreateCommand(
                mcpId, "MCP", "streamable_http", "{\"url\":\"https://example.test\"}", 5, 1);
    }

    private static final class InMemoryRepository implements IAiClientToolMcpConfigRepository {
        private AiClientToolMcpConfig existing;
        private AiClientToolMcpConfig inserted;
        private List<AiClientToolMcpConfig> all = List.of();

        @Override public boolean insert(AiClientToolMcpConfig config) { inserted = config; return true; }
        @Override public boolean updateById(AiClientToolMcpConfig config) { return true; }
        @Override public boolean updateByMcpId(AiClientToolMcpConfig config) { return true; }
        @Override public boolean deleteById(Long id) { return true; }
        @Override public boolean deleteByMcpId(String mcpId) { return true; }
        @Override public Optional<AiClientToolMcpConfig> findById(Long id) { return Optional.empty(); }
        @Override public Optional<AiClientToolMcpConfig> findByMcpId(String mcpId) { return Optional.ofNullable(existing); }
        @Override public List<AiClientToolMcpConfig> findAll() { return all; }
        @Override public List<AiClientToolMcpConfig> findByStatus(Integer status) { return all; }
        @Override public List<AiClientToolMcpConfig> findByTransportType(String transportType) { return all; }
        @Override public List<AiClientToolMcpConfig> findEnabled() { return all; }
    }
}
