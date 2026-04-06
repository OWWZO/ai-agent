package org.wwz.ai.domain.agent.reactor.agent.tool.mcp.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;

/**
 * MCP 运行时工厂。
 * 目前只实现 SSE，接口层先把 Streamable HTTP 的分支预留出来。
 */
@Slf4j
@Component
public class McpClientRuntimeFactory {

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

            // 将完整 SSE 地址拆成基础地址和端点，便于后续平滑扩展更多协议。
            String baseUri = StringUtils.defaultIfBlank(descriptor.getBaseUri(), uri.getScheme() + "://" + uri.getAuthority());
            String endpoint = StringUtils.defaultIfBlank(descriptor.getEndpoint(), buildEndpoint(uri));

            HttpClientSseClientTransport transport = HttpClientSseClientTransport.builder(baseUri)
                    .sseEndpoint(endpoint)
                    .build();

            int requestTimeout = descriptor.getRequestTimeout() == null ? 180 : descriptor.getRequestTimeout();
            McpSyncClient syncClient = McpClient.sync(transport)
                    .requestTimeout(Duration.ofMinutes(requestTimeout))
                    .build();

            syncClient.initialize();

            descriptor.setBaseUri(baseUri);
            descriptor.setEndpoint(endpoint);

            log.info("MCP SSE 客户端初始化成功: serverKey={}, baseUri={}, endpoint={}",
                    descriptor.resolveServerKey(), baseUri, endpoint);

            return McpClientRuntime.builder()
                    .descriptor(descriptor)
                    .syncClient(syncClient)
                    .build();
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
                            new StdioClientTransport(serverParameters, new JacksonMcpJsonMapper(new ObjectMapper())))
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
     * 未来接入 Streamable HTTP 时扩展此分支。
     */
    public McpClientRuntime createStreamableHttpRuntime(McpServerDescriptor descriptor) {
        log.error("当前版本暂不支持 Streamable HTTP: serverKey={}, serverUrl={}",
                descriptor.resolveServerKey(), descriptor.getServerUrl());
        throw new UnsupportedOperationException("Streamable HTTP transport is not supported yet");
    }

    /**
     * 从完整 URL 中提取 SSE 端点，保留 query 参数。
     */
    private String buildEndpoint(URI uri) {
        String path = StringUtils.defaultIfBlank(uri.getRawPath(), "/sse");
        String query = StringUtils.isBlank(uri.getRawQuery()) ? "" : "?" + uri.getRawQuery();
        return path + query;
    }
}
