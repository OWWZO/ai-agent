package org.wwz.ai.domain.agent.runtime.tool.mcp.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.client.transport.WebClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.WebFluxSseClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.time.Duration;

/**
 * MCP 运行时工厂。
 * 统一创建 SSE、STDIO、Streamable HTTP 三种运行时。
 */
@Slf4j
@Component
public class McpClientRuntimeFactory {

    private static final McpJsonMapper MCP_JSON_MAPPER = new JacksonMcpJsonMapper(new ObjectMapper());

    /**
     * 根据服务描述创建运行时。
     */
    public McpClientRuntime createRuntime(McpServerDescriptor descriptor) {
        if (descriptor == null) {
            throw new IllegalArgumentException("MCP server descriptor can not be null");
        }

        String transportType = StringUtils.defaultIfBlank(descriptor.getTransportType(), McpServerDescriptor.TRANSPORT_TYPE_SSE);
        descriptor.setTransportType(transportType);
        descriptor.setServerKey(descriptor.resolveServerKey());

        return switch (transportType) {
            case McpServerDescriptor.TRANSPORT_TYPE_SSE -> createSseRuntime(descriptor);
            case McpServerDescriptor.TRANSPORT_TYPE_STDIO -> createStdioRuntime(descriptor);
            case McpServerDescriptor.TRANSPORT_TYPE_STREAMABLE_HTTP -> createStreamableHttpRuntime(descriptor);
            default -> {
                log.error("不支持的 MCP 传输协议: serverKey={}, transportType={}",
                        descriptor.resolveServerKey(), transportType);
                throw new IllegalArgumentException("Unsupported MCP transport type: " + transportType);
            }
        };
    }

    /**
     * 创建 SSE 类型的 MCP 运行时。
     */
    public McpClientRuntime createSseRuntime(McpServerDescriptor descriptor) {
        try {
            URI uri = URI.create(StringUtils.trimToEmpty(descriptor.getServerUrl()));
            if (uri.getScheme() == null || uri.getAuthority() == null) {
                throw new IllegalArgumentException("Invalid SSE server url: " + descriptor.getServerUrl());
            }

            String baseUri = StringUtils.defaultIfBlank(descriptor.getBaseUri(), uri.getScheme() + "://" + uri.getAuthority());
            String endpoint = StringUtils.defaultIfBlank(descriptor.getEndpoint(), buildEndpoint(uri, "/sse"));

            WebFluxSseClientTransport transport = WebFluxSseClientTransport.builder(buildWebClientBuilder(baseUri, descriptor))
                    .sseEndpoint(endpoint)
                    .jsonMapper(MCP_JSON_MAPPER)
                    .build();

            descriptor.setBaseUri(baseUri);
            descriptor.setEndpoint(endpoint);
            return createHttpRuntime(descriptor, transport, "SSE");
        } catch (Exception e) {
            log.error("MCP SSE 客户端初始化失败: serverKey={}, serverUrl={}, reason={}",
                    descriptor.resolveServerKey(), descriptor.getServerUrl(), e.getMessage(), e);
            throw new IllegalStateException("Failed to create SSE MCP runtime for " + descriptor.getServerUrl(), e);
        }
    }

    /**
     * 创建 STDIO 类型的 MCP 运行时。
     */
    public McpClientRuntime createStdioRuntime(McpServerDescriptor descriptor) {
        try {
            if (StringUtils.isBlank(descriptor.getCommand())) {
                throw new IllegalArgumentException("Invalid STDIO command for mcpId: " + descriptor.getMcpId());
            }

            ServerParameters serverParameters = ServerParameters.builder(descriptor.getCommand())
                    .args(descriptor.getArgs())
                    .env(descriptor.getEnv())
                    .build();

            int requestTimeout = descriptor.getRequestTimeout() == null ? 180 : descriptor.getRequestTimeout();
            McpSyncClient syncClient = McpClient.sync(
                            new StdioClientTransport(serverParameters, MCP_JSON_MAPPER))
                    .requestTimeout(Duration.ofSeconds(requestTimeout))
                    .build();

            syncClient.initialize();

            log.info("MCP STDIO 客户端初始化成功: serverKey={}, command={}",
                    descriptor.resolveServerKey(), descriptor.getCommand());

            return McpClientRuntime.builder()
                    .descriptor(descriptor)
                    .syncClient(syncClient)
                    .build();
        } catch (Exception e) {
            log.error("MCP STDIO 客户端初始化失败: serverKey={}, command={}, reason={}",
                    descriptor.resolveServerKey(), descriptor.getCommand(), e.getMessage(), e);
            throw new IllegalStateException("Failed to create STDIO MCP runtime for " + descriptor.resolveServerKey(), e);
        }
    }

    /**
     * 创建 Streamable HTTP 类型的 MCP 运行时。
     */
    public McpClientRuntime createStreamableHttpRuntime(McpServerDescriptor descriptor) {
        try {
            URI uri = URI.create(StringUtils.trimToEmpty(descriptor.getServerUrl()));
            if (uri.getScheme() == null || uri.getAuthority() == null) {
                throw new IllegalArgumentException("Invalid Streamable HTTP server url: " + descriptor.getServerUrl());
            }

            String baseUri = StringUtils.defaultIfBlank(descriptor.getBaseUri(), uri.getScheme() + "://" + uri.getAuthority());
            String endpoint = StringUtils.defaultIfBlank(descriptor.getEndpoint(), buildEndpoint(uri, "/mcp"));
            boolean openConnectionOnStartup = !Boolean.FALSE.equals(descriptor.getOpenConnectionOnStartup());
            descriptor.setBaseUri(baseUri);
            descriptor.setEndpoint(endpoint);

            try {
                return createHttpRuntime(descriptor,
                        buildStreamableHttpTransport(descriptor, openConnectionOnStartup),
                        "Streamable HTTP");
            } catch (Exception e) {
                if (shouldFallbackToLazyConnection(descriptor, openConnectionOnStartup, e)) {
                    log.warn("MCP Streamable HTTP 预连接不兼容，降级为按需连接模式: serverKey={}, serverUrl={}",
                            descriptor.resolveServerKey(), descriptor.getServerUrl());
                    descriptor.setOpenConnectionOnStartup(false);
                    return createHttpRuntime(descriptor,
                            buildStreamableHttpTransport(descriptor, false),
                            "Streamable HTTP");
                }
                throw e;
            }
        } catch (Exception e) {
            log.error("MCP Streamable HTTP 客户端初始化失败: serverKey={}, serverUrl={}, reason={}",
                    descriptor.resolveServerKey(), descriptor.getServerUrl(), e.getMessage(), e);
            throw new IllegalStateException("Failed to create Streamable HTTP MCP runtime for " + descriptor.getServerUrl(), e);
        }
    }

    /**
     * 统一创建 HTTP 类型运行时。
     */
    private McpClientRuntime createHttpRuntime(McpServerDescriptor descriptor,
                                               io.modelcontextprotocol.spec.McpClientTransport transport,
                                               String transportName) {
        int requestTimeout = descriptor.getRequestTimeout() == null ? 180 : descriptor.getRequestTimeout();
        McpSyncClient syncClient = McpClient.sync(transport)
                .requestTimeout(Duration.ofMinutes(requestTimeout))
                .build();

        syncClient.initialize();

        log.info("MCP {} 客户端初始化成功: serverKey={}, baseUri={}, endpoint={}",
                transportName, descriptor.resolveServerKey(), descriptor.getBaseUri(), descriptor.getEndpoint());

        return McpClientRuntime.builder()
                .descriptor(descriptor)
                .syncClient(syncClient)
                .build();
    }

    /**
     * 构建 Streamable HTTP transport，便于统一复用初始化参数。
     */
    private WebClientStreamableHttpTransport buildStreamableHttpTransport(McpServerDescriptor descriptor,
                                                                          boolean openConnectionOnStartup) {
        return WebClientStreamableHttpTransport.builder(buildWebClientBuilder(descriptor.getBaseUri(), descriptor))
                .endpoint(descriptor.getEndpoint())
                .jsonMapper(MCP_JSON_MAPPER)
                .resumableStreams(Boolean.TRUE.equals(descriptor.getResumableStreams()))
                .openConnectionOnStartup(openConnectionOnStartup)
                .build();
    }

    /**
     * 部分服务端会在 GET /mcp 上返回 200 + application/json 说明信息，
     * 这种实现不支持 SDK 的启动期预连接，需要自动降级为按需连接模式。
     */
    private boolean shouldFallbackToLazyConnection(McpServerDescriptor descriptor,
                                                   boolean openConnectionOnStartup,
                                                   Exception exception) {
        if (descriptor == null || !openConnectionOnStartup) {
            return false;
        }

        Throwable current = exception;
        while (current != null) {
            if (current instanceof WebClientResponseException responseException) {
                String message = StringUtils.defaultString(responseException.getMessage());
                if (responseException.getStatusCode().is2xxSuccessful() && message.contains(" from GET ")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * 构建 HTTP 客户端，统一复用超时、基础地址和默认请求头配置。
     */
    private WebClient.Builder buildWebClientBuilder(String baseUri, McpServerDescriptor descriptor) {
        WebClient.Builder builder = WebClient.builder()
                .baseUrl(baseUri);

        if (descriptor.getHeaders() != null && !descriptor.getHeaders().isEmpty()) {
            builder.defaultHeaders(headers -> descriptor.getHeaders().forEach(headers::add));
        }

        return builder;
    }

    /**
     * 从完整 URL 中提取端点，保留 query 参数。
     */
    private String buildEndpoint(URI uri, String defaultEndpoint) {
        String path = StringUtils.defaultIfBlank(uri.getRawPath(), defaultEndpoint);
        String query = StringUtils.isBlank(uri.getRawQuery()) ? "" : "?" + uri.getRawQuery();
        return path + query;
    }
}
