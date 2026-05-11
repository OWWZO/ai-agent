package org.wwz.ai.domain.agent.runtime.tool.mcp.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 服务描述对象。
 * 统一承载 SSE、STDIO、Streamable HTTP 三种协议的运行时参数。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpServerDescriptor {

    public static final String TRANSPORT_TYPE_SSE = "sse";
    public static final String TRANSPORT_TYPE_STDIO = "stdio";
    public static final String TRANSPORT_TYPE_STREAMABLE_HTTP = "streamable_http";

    /**
     * MCP 业务标识。
     */
    private String mcpId;

    /**
     * 配置中的原始 MCP 服务地址。
     */
    private String serverUrl;

    /**
     * 传输协议类型，支持 sse/stdio/streamable_http。
     */
    @Builder.Default
    private String transportType = TRANSPORT_TYPE_SSE;

    /**
     * 服务唯一标识，默认复用 serverUrl。
     */
    private String serverKey;

    /**
     * HTTP 类协议的基础地址。
     */
    private String baseUri;

    /**
     * HTTP 类协议的端点。
     */
    private String endpoint;

    /**
     * 请求超时时间，沿用数据库配置定义。
     */
    private Integer requestTimeout;

    /**
     * STDIO 启动命令。
     */
    private String command;

    /**
     * STDIO 启动参数。
     */
    @Builder.Default
    private List<String> args = new ArrayList<>();

    /**
     * STDIO 环境变量。
     */
    @Builder.Default
    private Map<String, String> env = new HashMap<>();

    /**
     * HTTP 类协议透传请求头。
     */
    @Builder.Default
    private Map<String, String> headers = new HashMap<>();

    /**
     * Streamable HTTP 是否启用可恢复流。
     */
    @Builder.Default
    private Boolean resumableStreams = false;

    /**
     * Streamable HTTP 是否在启动时主动建立连接。
     */
    @Builder.Default
    private Boolean openConnectionOnStartup = true;

    /**
     * 构建默认 SSE 描述对象。
     */
    public static McpServerDescriptor sse(String serverUrl) {
        return McpServerDescriptor.builder()
                .serverUrl(serverUrl)
                .serverKey(serverUrl)
                .transportType(TRANSPORT_TYPE_SSE)
                .build();
    }

    /**
     * 获取稳定的服务标识。
     */
    public String resolveServerKey() {
        if (StringUtils.isNotBlank(serverKey)) {
            return serverKey;
        }
        if (StringUtils.isNotBlank(mcpId)) {
            return mcpId;
        }
        return serverUrl;
    }
}
