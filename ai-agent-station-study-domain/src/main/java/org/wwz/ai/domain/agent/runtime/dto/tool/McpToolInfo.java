package org.wwz.ai.domain.agent.runtime.dto.tool;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.wwz.ai.domain.agent.runtime.tool.mcp.runtime.McpServerDescriptor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpToolInfo {

    /**
     * MCP 配置主键业务标识。
     */
    private String mcpId;

    /**
     * MCP 工具名称。
     */
    private String name;

    /**
     * MCP 工具描述，供提示词和原生 function call 使用。
     */
    private String desc;

    /**
     * MCP 工具参数 Schema，沿用 JSON 字符串格式以兼容现有链路。
     */
    private String parameters;

    /**
     * 传输协议类型，支持 sse/stdio/streamable_http。
     */
    private String transportType;

    /**
     * 服务唯一标识，默认与 serverUrl 相同。
     */
    private String serverKey;

    /**
     * 运行时服务描述，仅用于本地执行，不参与序列化。
     */
    @ToString.Exclude
    @JSONField(serialize = false, deserialize = false)
    private McpServerDescriptor descriptor;
}
