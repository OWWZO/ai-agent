package org.wwz.ai.infrastructure.dao.po.catalog;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Catalog 上下文 MCP 配置持久化对象。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiClientToolMcpPO implements Serializable {
    private Long id;
    private String mcpId;
    private String mcpName;
    private String transportType;
    private String transportConfig;
    private Integer requestTimeout;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
