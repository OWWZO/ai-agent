package org.wwz.ai.domain.agent.runtime.dto.tool;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.wwz.ai.domain.agent.runtime.tool.mcp.runtime.McpServerDescriptor;

/**
 * MCP 工具元数据模型，描述工具名称、说明和输入 schema。
 * <p>
 * {@code name} 为模型可见 FQ 名（mcp__server__tool）；
 * {@code originalName} 为 MCP tools/call 的服务端原始名。
 */
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
     * 模型可见工具名（FQ：mcp__{server}__{tool}）。
     */
    private String name;

    /**
     * 服务端原始工具名（callTool 使用）。
     */
    private String originalName;

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
     * 是否始终加载（不走 ToolSearch 延迟）。
     */
    private Boolean alwaysLoad;

    /**
     * ToolSearch 额外评分提示（来自 _meta）。
     */
    private String searchHint;

    /**
     * 只读提示（annotations.readOnlyHint）。
     */
    private Boolean readOnlyHint;

    /**
     * 破坏性提示（annotations.destructiveHint）。
     */
    private Boolean destructiveHint;

    /**
     * 运行时服务描述，仅用于本地执行，不参与序列化。
     */
    @ToString.Exclude
    @JSONField(serialize = false, deserialize = false)
    private McpServerDescriptor descriptor;

    /**
     * 供 wire call 使用的原始名；缺省回退 name。
     */
    public String resolveWireName() {
        return originalName != null && !originalName.isBlank() ? originalName : name;
    }
}
