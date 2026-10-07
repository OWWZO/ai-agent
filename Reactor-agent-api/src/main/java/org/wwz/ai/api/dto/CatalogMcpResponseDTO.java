package org.wwz.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 登录用户可见的 MCP 安全元数据。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CatalogMcpResponseDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String mcpId;

    private String mcpName;

    private String transportType;

    private Integer status;
}
