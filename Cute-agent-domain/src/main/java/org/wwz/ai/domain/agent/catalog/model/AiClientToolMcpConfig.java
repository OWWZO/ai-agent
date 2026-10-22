package org.wwz.ai.domain.agent.catalog.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * MCP 连接配置领域模型。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiClientToolMcpConfig {

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
