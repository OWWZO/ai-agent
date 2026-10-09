package org.wwz.ai.domain.agent.catalog.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * API 连接配置领域模型。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiClientApiConfig {

    private Long id;
    private String apiId;
    private String baseUrl;
    private String apiKey;
    private String completionsPath;
    private String embeddingsPath;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
