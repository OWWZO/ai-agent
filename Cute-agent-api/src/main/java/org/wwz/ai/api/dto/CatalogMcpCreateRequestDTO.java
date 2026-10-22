package org.wwz.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 登录用户创建 MCP 连接器时使用的请求 DTO。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CatalogMcpCreateRequestDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String mcpId;
    private String mcpName;
    private String transportType;
    private String transportConfig;
    private Integer requestTimeout;
    private Integer status;
}
