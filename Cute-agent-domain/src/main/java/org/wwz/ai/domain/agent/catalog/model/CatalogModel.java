package org.wwz.ai.domain.agent.catalog.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录用户可选择的模型目录项。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogModel {

    private String modelId;
    private String modelName;
    private String modelType;
    private Integer supportsThinking;
    private Integer contextWindow;
    private Integer status;
}
