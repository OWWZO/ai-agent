package org.wwz.ai.domain.agent.runtime.llm;

import lombok.Builder;
import lombok.Value;

/**
 * 可供登录用户选择的模型目录项，不携带 API 凭据或连接地址。
 */
@Value
@Builder
public class LlmModelCatalogEntry {

    String modelId;
    String modelName;
    String modelType;
    Integer supportsThinking;
    Integer contextWindow;
    Integer status;
}
