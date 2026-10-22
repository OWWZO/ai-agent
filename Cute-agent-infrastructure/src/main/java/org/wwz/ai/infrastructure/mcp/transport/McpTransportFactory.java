package org.wwz.ai.infrastructure.mcp.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.client.transport.WebClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.WebFluxSseClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import org.springframework.web.reactive.function.client.WebClient;
import org.wwz.ai.infrastructure.mcp.model.McpServerDescriptor;

/**
 * MCP transport 构造器。
 *
 * <p>这里集中封装 SDK transport 和 WebClient 的创建，客户端工厂只负责同步客户端生命周期。</p>
 */
public class McpTransportFactory {

    private static final McpJsonMapper MCP_JSON_MAPPER = new JacksonMcpJsonMapper(new ObjectMapper());

    public McpClientTransport createSseTransport(McpServerDescriptor descriptor,
                                                  String baseUri,
                                                  String endpoint) {
        return WebFluxSseClientTransport.builder(buildWebClientBuilder(baseUri, descriptor))
                .sseEndpoint(endpoint)
                .jsonMapper(MCP_JSON_MAPPER)
                .build();
    }

    public StdioClientTransport createStdioTransport(McpServerDescriptor descriptor) {
        ServerParameters serverParameters = ServerParameters.builder(descriptor.getCommand())
                .args(descriptor.getArgs())
                .env(descriptor.getEnv())
                .build();
        return new StdioClientTransport(serverParameters, MCP_JSON_MAPPER);
    }

    public WebClientStreamableHttpTransport createStreamableHttpTransport(McpServerDescriptor descriptor,
                                                                            boolean openConnectionOnStartup) {
        return WebClientStreamableHttpTransport.builder(
                        buildWebClientBuilder(descriptor.getBaseUri(), descriptor))
                .endpoint(descriptor.getEndpoint())
                .jsonMapper(MCP_JSON_MAPPER)
                .resumableStreams(Boolean.TRUE.equals(descriptor.getResumableStreams()))
                .openConnectionOnStartup(openConnectionOnStartup)
                .build();
    }

    private WebClient.Builder buildWebClientBuilder(String baseUri, McpServerDescriptor descriptor) {
        WebClient.Builder builder = WebClient.builder().baseUrl(baseUri);
        if (descriptor.getHeaders() != null && !descriptor.getHeaders().isEmpty()) {
            builder.defaultHeaders(headers -> descriptor.getHeaders().forEach(headers::add));
        }
        return builder;
    }
}
