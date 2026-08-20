package org.wwz.ai.domain.agent.runtime;

import lombok.Builder;
import lombok.Value;
import org.wwz.ai.domain.agent.runtime.llm.DomainMessageConverter;
import org.wwz.ai.domain.agent.runtime.llm.LlmChatModelResolver;
import org.wwz.ai.domain.agent.runtime.llm.LlmChatResponseMapper;
import org.wwz.ai.domain.agent.runtime.llm.LlmModelCatalog;
import org.wwz.ai.domain.agent.runtime.llm.OpenAiChatOptionsFactory;
import org.wwz.ai.domain.agent.runtime.llm.OpenAiCompatibleSseChatStreamClient;
import org.wwz.ai.domain.agent.runtime.llm.StreamResponseHandler;

/**
 * LLM 运行时依赖集合。
 * 统一承接 Spring AI 适配器，避免 LLM 在运行期自行回 Spring 容器拉取 Bean。
 */
@Value
@Builder
public class ReactorLlmDependencies {

    LlmChatModelResolver chatModelResolver;

    OpenAiChatOptionsFactory chatOptionsFactory;

    DomainMessageConverter messageConverter;

    LlmChatResponseMapper responseMapper;

    StreamResponseHandler streamResponseHandler;

    /**
     * 可选：旁路 Spring AI tool-call window 合并的 OpenAI 兼容 SSE 直解客户端。
     * 为空时 function_call 流式回退 chatModel.stream()。
     */
    OpenAiCompatibleSseChatStreamClient openAiCompatibleSseChatStreamClient;

    /** 可选：DB 模型目录；空则 resolveLlmSettings 仅走 yml。 */
    LlmModelCatalog modelCatalog;
}

