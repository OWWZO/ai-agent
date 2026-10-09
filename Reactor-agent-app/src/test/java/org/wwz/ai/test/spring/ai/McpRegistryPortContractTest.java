package org.wwz.ai.test.spring.ai;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.wwz.ai.domain.agent.adapter.repository.IAgentRepository;
import org.wwz.ai.domain.agent.runtime.tool.mcp.port.McpToolExecutor;
import org.wwz.ai.infrastructure.mcp.registry.McpRegistry;

import java.lang.reflect.Field;
import java.util.List;

/** Registry 的生产实现只通过领域 MCP Port 对外提供能力。 */
public class McpRegistryPortContractTest {

    @Test
    public void shouldImplementTypedPortWithoutCreatingClientsForEmptyConfiguration() throws Exception {
        McpRegistry registry = new McpRegistry();
        IAgentRepository repository = Mockito.mock(IAgentRepository.class);
        Mockito.when(repository.queryEnabledAiClientToolMcpVOList()).thenReturn(List.of());
        setField(registry, "repository", repository);

        Assert.assertTrue(registry instanceof McpToolExecutor);
        McpToolExecutor executor = registry;
        executor.reload();

        Assert.assertTrue(executor.listGlobalEnabledTools().isEmpty());
        Assert.assertTrue(executor.listGlobalEnabledResources().isEmpty());
        Assert.assertFalse(executor.hasAnyResources());
        Mockito.verify(repository, Mockito.atLeastOnce()).queryEnabledAiClientToolMcpVOList();
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
