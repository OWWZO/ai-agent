package org.wwz.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 登录用户创建模型连接配置时使用的请求 DTO。
 *
 * <p>API Key 只允许出现在请求中，不会出现在目录响应里。</p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CatalogModelCreateRequestDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String apiId;
    private String baseUrl;
    private String apiKey;
    private String completionsPath;
    private String embeddingsPath;
    private String modelId;
    private String modelName;
    private String modelType;
    private String modelUsage;
    private Integer supportsThinking;
    private Integer contextWindow;
    private Integer status;
}
