package org.wwz.ai.domain.agent.reactor.agent.tool.mcp.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * MCP 服务描述对象。
 * 当前只落地 SSE，但字段设计预留给后续 Streamable HTTP 扩展。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpServerDescriptor {

    public static final String TRANSPORT_TYPE_SSE = "sse";
    public static final String TRANSPORT_TYPE_STREAMABLE_HTTP = "streamable_http";

    /**
     * 配置中的原始 MCP 服务地址。
     */
    private String serverUrl;

    /**
     * 传输协议类型，本次固定为 sse。
     */
    @Builder.Default
    private String transportType = TRANSPORT_TYPE_SSE;

    /**
     * 服务唯一标识，默认复用 serverUrl。
     */
    private String serverKey;

    /**
     * 预留字段：后续可直接指定基础地址。
     */
    private String baseUri;

    /**
     * 预留字段：后续可直接指定协议端点。
     */
    private String endpoint;

    /**
     * 预留字段：后续可透传鉴权头。
     */
    @Builder.Default
    private Map<String, String> headers = new HashMap<>();

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
        return StringUtils.isNotBlank(serverKey) ? serverKey : serverUrl;
    }
}
