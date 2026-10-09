package org.wwz.ai.application.catalog.mcp;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.application.catalog.ApplicationResult;
import org.wwz.ai.domain.agent.catalog.model.AiClientToolMcpConfig;
import org.wwz.ai.domain.agent.catalog.model.McpRuntimeReloadResult;
import org.wwz.ai.domain.agent.catalog.port.IAiClientToolMcpConfigRepository;
import org.wwz.ai.domain.agent.catalog.port.IMcpRuntimeReloadPort;

import java.util.List;
import java.util.Optional;

public class McpConfigApplicationServiceTest {

    @Test
    public void shouldApplyTheSameDuplicateRuleToAdminMcpCreation() {
        Repository repository = new Repository();
        repository.existing = AiClientToolMcpConfig.builder().mcpId("mcp-1").build();
        McpConfigApplicationService service = new McpConfigApplicationService(repository,
                McpRuntimeReloadResult::succeeded);

        ApplicationResult<Boolean> result = service.create(new McpConfigCommand(
                null, "mcp-1", "MCP", "streamable_http", "{}", 5, 1));

        Assert.assertEquals("0002", result.code());
        Assert.assertFalse(result.data());
        Assert.assertNull(repository.inserted);
    }

    @Test
    public void shouldReportReloadFailureAfterAdminWrite() {
        Repository repository = new Repository();
        McpConfigApplicationService service = new McpConfigApplicationService(repository,
                () -> McpRuntimeReloadResult.failed("internal client error"));

        ApplicationResult<Boolean> result = service.create(new McpConfigCommand(
                null, "mcp-1", "MCP", "streamable_http", "{}", 5, 1));

        Assert.assertEquals("0000", result.code());
        Assert.assertTrue(result.data());
        Assert.assertEquals("MCP 配置已写入，但运行时刷新失败", result.info());
    }

    private static final class Repository implements IAiClientToolMcpConfigRepository {
        private AiClientToolMcpConfig existing;
        private AiClientToolMcpConfig inserted;

        @Override public boolean insert(AiClientToolMcpConfig config) { inserted = config; return true; }
        @Override public boolean updateById(AiClientToolMcpConfig config) { return true; }
        @Override public boolean updateByMcpId(AiClientToolMcpConfig config) { return true; }
        @Override public boolean deleteById(Long id) { return true; }
        @Override public boolean deleteByMcpId(String mcpId) { return true; }
        @Override public Optional<AiClientToolMcpConfig> findById(Long id) { return Optional.empty(); }
        @Override public Optional<AiClientToolMcpConfig> findByMcpId(String mcpId) { return Optional.ofNullable(existing); }
        @Override public List<AiClientToolMcpConfig> findAll() { return List.of(); }
        @Override public List<AiClientToolMcpConfig> findByStatus(Integer status) { return List.of(); }
        @Override public List<AiClientToolMcpConfig> findByTransportType(String transportType) { return List.of(); }
        @Override public List<AiClientToolMcpConfig> findEnabled() { return List.of(); }
    }
}
