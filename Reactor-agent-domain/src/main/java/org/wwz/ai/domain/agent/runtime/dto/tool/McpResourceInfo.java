package org.wwz.ai.domain.agent.runtime.dto.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP Resource 元数据（resources/list）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpResourceInfo {

    private String mcpId;

    /** 展示用 server 标识（与工具 FQ 名 server 段一致）。 */
    private String serverKey;

    private String uri;

    private String name;

    private String title;

    private String description;

    private String mimeType;

    private Long size;
}
