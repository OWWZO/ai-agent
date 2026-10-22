package org.wwz.ai.domain.agent.catalog.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 模型配置领域模型。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiClientModelConfig {

    private Long id;
    private String modelId;
    private String apiId;
    private String modelName;
    private String modelType;
    private String modelUsage;
    private Integer supportsThinking;
    private Integer contextWindow;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
