package org.wwz.ai.test.spring.ai;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.WebFluxStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.wwz.ai.domain.agent.model.valobj.AiClientToolMcpVO;
import org.wwz.ai.domain.agent.runtime.tool.mcp.runtime.McpClientRuntime;
import org.wwz.ai.domain.agent.runtime.tool.mcp.runtime.McpClientRuntimeFactory;
import org.wwz.ai.domain.agent.runtime.tool.mcp.runtime.McpRegistry;
import org.wwz.ai.domain.agent.runtime.tool.mcp.runtime.McpServerDescriptor;
import org.wwz.ai.infrastructure.adapter.repository.AgentRepository;
import org.wwz.ai.infrastructure.dao.po.AiClientToolMcp;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Streamable HTTP MCP 支持测试
 */
public class McpStreamableHttpSupportTest {

    @Test
    public void test_parseStreamableHttpTransportConfig() throws Exception {
        AgentRepository repository = new AgentRepository();
        Method method = AgentRepository.class.getDeclaredMethod("toAiClientToolMcpVO", AiClientToolMcp.class);
        method.setAccessible(true);

        AiClientToolMcp toolMcp = AiClientToolMcp.builder()
                .mcpId("streamable-test")
                .mcpName("测试Streamable工具")
                .transportType("streamable_http")
                .transportConfig("{\"baseUri\":\"http://127.0.0.1:8101\",\"endpoint\":\"/mcp\",\"headers\":{\"Authorization\":\"Bearer demo\"},\"resumableStreams\":true,\"openConnectionOnStartup\":false}")
                .requestTimeout(180)
                .status(1)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        AiClientToolMcpVO mcpVO = (AiClientToolMcpVO) method.invoke(repository, toolMcp);

        Assert.assertNotNull(mcpVO);
        Assert.assertNotNull(mcpVO.getTransportConfigStreamableHttp());
        Assert.assertEquals("http://127.0.0.1:8101", mcpVO.getTransportConfigStreamableHttp().getBaseUri());
        Assert.assertEquals("/mcp", mcpVO.getTransportConfigStreamableHttp().getEndpoint());
        Assert.assertEquals("Bearer demo", mcpVO.getTransportConfigStreamableHttp().getHeaders().get("Authorization"));
        Assert.assertTrue(Boolean.TRUE.equals(mcpVO.getTransportConfigStreamableHttp().getResumableStreams()));
        Assert.assertTrue(Boolean.FALSE.equals(mcpVO.getTransportConfigStreamableHttp().getOpenConnectionOnStartup()));
    }

    @Test
    public void test_buildStreamableHttpDescriptor() throws Exception {
        McpRegistry registry = new McpRegistry();
        Method method = McpRegistry.class.getDeclaredMethod("buildDescriptor", AiClientToolMcpVO.class);
        method.setAccessible(true);

        AiClientToolMcpVO mcpVO = AiClientToolMcpVO.builder()
                .mcpId("streamable-test")
                .mcpName("测试Streamable工具")
                .transportType(McpServerDescriptor.TRANSPORT_TYPE_STREAMABLE_HTTP)
                .requestTimeout(60)
                .transportConfigStreamableHttp(AiClientToolMcpVO.TransportConfigStreamableHttp.builder()
                        .baseUri("http://127.0.0.1:8101")
                        .endpoint("/mcp")
                        .headers(Map.of("Authorization", "Bearer demo"))
                        .resumableStreams(true)
                        .openConnectionOnStartup(false)
                        .build())
                .build();

        McpServerDescriptor descriptor = (McpServerDescriptor) method.invoke(registry, mcpVO);

        Assert.assertNotNull(descriptor);
        Assert.assertEquals("http://127.0.0.1:8101/mcp", descriptor.getServerUrl());
        Assert.assertEquals("http://127.0.0.1:8101", descriptor.getBaseUri());
        Assert.assertEquals("/mcp", descriptor.getEndpoint());
        Assert.assertEquals("Bearer demo", descriptor.getHeaders().get("Authorization"));
        Assert.assertTrue(Boolean.TRUE.equals(descriptor.getResumableStreams()));
        Assert.assertTrue(Boolean.FALSE.equals(descriptor.getOpenConnectionOnStartup()));
    }

    @Test
    public void test_createStreamableHttpRuntimeAndListTools() throws Exception {
        TestStreamableServer testServer = startStreamableServer();
        McpClientRuntimeFactory runtimeFactory = new McpClientRuntimeFactory();

        McpServerDescriptor descriptor = McpServerDescriptor.builder()
                .mcpId("streamable-runtime-test")
                .serverKey("streamable-runtime-test")
                .transportType(McpServerDescriptor.TRANSPORT_TYPE_STREAMABLE_HTTP)
                .serverUrl(testServer.baseUri() + "/mcp")
                .baseUri(testServer.baseUri())
                .endpoint("/mcp")
                .headers(Map.of("Authorization", "Bearer local-test"))
                .requestTimeout(1)
                .resumableStreams(false)
                .openConnectionOnStartup(true)
                .build();

        McpClientRuntime runtime = null;
        try {
            runtime = runtimeFactory.createStreamableHttpRuntime(descriptor);
            McpSchema.ListToolsResult listToolsResult = runtime.getSyncClient().listTools();

            Assert.assertNotNull(listToolsResult);
            Assert.assertNotNull(listToolsResult.tools());
            Assert.assertFalse(listToolsResult.tools().isEmpty());
            Assert.assertEquals("ping_tool", listToolsResult.tools().get(0).name());
        } finally {
            if (runtime != null && runtime.getSyncClient() != null) {
                runtime.getSyncClient().closeGracefully();
            }
            testServer.close();
        }
    }

    /**
     * 启动一个最小可用的 Streamable HTTP MCP 服务，供运行时初始化测试使用。
     */
    private TestStreamableServer startStreamableServer() {
        WebFluxStreamableServerTransportProvider provider = WebFluxStreamableServerTransportProvider.builder()
                .messageEndpoint("/mcp")
                .build();

        McpSchema.JsonSchema inputSchema = new McpSchema.JsonSchema(
                "object",
                Collections.emptyMap(),
                Collections.emptyList(),
                false,
                Collections.emptyMap(),
                Collections.emptyMap()
        );

        McpSchema.Tool pingTool = new McpSchema.Tool(
                "ping_tool",
                "Ping Tool",
                "测试Streamable HTTP工具",
                inputSchema,
                Collections.emptyMap(),
                null,
                Collections.emptyMap()
        );

        McpSyncServer mcpServer = McpServer.sync(provider)
                .serverInfo("test-streamable-server", "1.0.0")
                .tool(pingTool, (exchange, arguments) -> new McpSchema.CallToolResult(List.of(new McpSchema.TextContent("pong")), false))
                .build();

        HttpHandler httpHandler = RouterFunctions.toHttpHandler(provider.getRouterFunction());
        DisposableServer server = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle(new ReactorHttpHandlerAdapter(httpHandler))
                .bindNow();

        return new TestStreamableServer(server, provider, mcpServer);
    }

    /**
     * 测试服务句柄，统一清理底层资源。
     */
    private record TestStreamableServer(DisposableServer server,
                                        WebFluxStreamableServerTransportProvider provider,
                                        McpSyncServer mcpServer) {

        private String baseUri() {
            return "http://127.0.0.1:" + server.port();
        }

        private void close() {
            mcpServer.closeGracefully();
            provider.closeGracefully().block();
            server.disposeNow();
        }
    }
}
