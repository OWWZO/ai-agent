package org.wwz.ai.infrastructure.dao.po.catalog;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Catalog 上下文模型配置持久化对象。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiClientModelPO implements Serializable {
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
