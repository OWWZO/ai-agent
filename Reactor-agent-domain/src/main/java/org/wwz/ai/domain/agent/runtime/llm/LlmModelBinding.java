package org.wwz.ai.domain.agent.runtime.llm;

import lombok.Builder;
import lombok.Value;

/**
 * 一次出站调用所需的模型绑定（来自 DB 管理台，不含 yml）。
 * <p>
 * [modelId] 是业务主键（前端/角色配置引用）；[modelName] 是上游模型名。
 */
@Value
@Builder
public class LlmModelBinding {

    String modelId;
    String modelName;
    String apiId;
    /** 模型用途；使用 fallback/backup/备用模型标记备用模型。 */
    String modelUsage;
    String baseUrl;
    String apiKey;
    /** 对话补全路径；空则调用方用默认 /v1/chat/completions 或 /chat/completions。 */
    String completionsPath;
    /** 上下文窗口（输入侧 token 上限）。 */
    Integer contextWindow;
}
