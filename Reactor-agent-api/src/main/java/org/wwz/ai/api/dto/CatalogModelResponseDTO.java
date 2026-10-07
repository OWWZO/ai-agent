package org.wwz.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 登录用户可见的模型安全元数据。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CatalogModelResponseDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String modelId;

    private String modelName;

    private String modelType;

    private Integer supportsThinking;

    private Integer contextWindow;

    /** 1 表示当前可用于对话选择。 */
    private Integer status;
}
