package org.wwz.ai.test.spring.ai;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
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
        // 运行时读路径必须与目录写入同源：只查启用项，且走 catalog 端口。
        Mockito.verify(configRepository, Mockito.atLeastOnce()).findEnabled();
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
