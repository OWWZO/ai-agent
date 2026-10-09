package org.wwz.ai.domain.agent.runtime;

import lombok.Builder;
import lombok.Value;
import org.wwz.ai.domain.agent.runtime.llm.LlmCompletionPort;
import org.wwz.ai.domain.agent.runtime.llm.LlmModelCatalog;
import org.wwz.ai.domain.agent.runtime.llm.StreamResponseHandler;

/**
 * LLM 运行时依赖集合。
 * Domain 只接收 LLM 出站 Port，避免运行期自行回 Spring 容器拉取供应商 Bean。
 */
@Value
@Builder
public class ReactorLlmDependencies {

    LlmCompletionPort completionPort;
    StreamResponseHandler streamResponseHandler;

    /** 可选：DB 模型目录；空则 resolveLlmSettings 仅走 yml。 */
    LlmModelCatalog modelCatalog;
}
