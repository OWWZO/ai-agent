package org.wwz.ai.test.spring.ai;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.wwz.ai.domain.agent.catalog.model.AiClientToolMcpConfig;
import org.wwz.ai.domain.agent.catalog.port.IAiClientToolMcpConfigRepository;
import org.wwz.ai.domain.agent.runtime.tool.mcp.port.McpToolExecutor;
import org.wwz.ai.infrastructure.adapter.repository.AgentRepository;
import org.wwz.ai.infrastructure.mcp.registry.McpRegistry;

import java.lang.reflect.Field;
import java.util.List;

/** Registry 的生产实现只通过领域 MCP Port 对外提供能力。 */
public class McpRegistryPortContractTest {

    @Test
    public void shouldImplementTypedPortWithoutCreatingClientsForEmptyConfiguration() throws Exception {
        McpRegistry registry = new McpRegistry();
        IAiClientToolMcpConfigRepository configRepository = Mockito.mock(IAiClientToolMcpConfigRepository.class);
        Mockito.when(configRepository.findEnabled()).thenReturn(List.of());
        setField(registry, "repository", new AgentRepository(configRepository));

        Assert.assertTrue(registry instanceof McpToolExecutor);
        McpToolExecutor executor = registry;
        executor.reload();

        Assert.assertTrue(executor.listGlobalEnabledTools().isEmpty());
        Assert.assertTrue(executor.listGlobalEnabledResources().isEmpty());
        Assert.assertFalse(executor.hasAnyResources());
        Mockito.verify(configRepository, Mockito.atLeastOnce()).findEnabled();
    }

    /**
     * 运行时读路径必须只取启用项。
     * <p>
     * 锁住的是「reload 只查 findEnabled」这条不变量，而不是「调了某个方法」——
     * 若有人把 findEnabled 换成 findAll，未启用配置会被一起预热并暴露给模型。
     */
    @Test
    public void reloadMustReadOnlyEnabledConfigurations() throws Exception {
        McpRegistry registry = new McpRegistry();
        IAiClientToolMcpConfigRepository configRepository = Mockito.mock(IAiClientToolMcpConfigRepository.class);
        Mockito.when(configRepository.findEnabled()).thenReturn(List.of(
                AiClientToolMcpConfig.builder()
                        .mcpId("enabled-mcp")
                        .mcpName("enabled")
                        .transportType("sse")
                        .status(1)
                        .build()));
        Mockito.when(configRepository.findAll()).thenReturn(List.of(
                AiClientToolMcpConfig.builder()
                        .mcpId("disabled-mcp")
                        .mcpName("disabled")
                        .transportType("sse")
                        .status(0)
                        .build()));
        setField(registry, "repository", new AgentRepository(configRepository));

        McpToolExecutor executor = registry;
        executor.reload();

        // 只读启用项；未启用的 findAll 结果绝不能进入运行时快照。
        Mockito.verify(configRepository, Mockito.atLeastOnce()).findEnabled();
        Mockito.verify(configRepository, Mockito.never()).findAll();
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
