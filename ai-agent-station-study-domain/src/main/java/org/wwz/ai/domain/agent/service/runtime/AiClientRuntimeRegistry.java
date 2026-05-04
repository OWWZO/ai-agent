package org.wwz.ai.domain.agent.service.runtime;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.api.OpenAiApi;

/**
 * AI Client 运行时注册表。
 * 用显式业务 ID 读写运行时对象，避免 domain 通过 Spring Bean 名称做动态查找。
 */
public interface AiClientRuntimeRegistry {

    void registerApi(String apiId, OpenAiApi openAiApi);

    void registerModel(String modelId, ChatModel chatModel);

    void registerAdvisor(String advisorId, Advisor advisor);

    void registerChatClient(String clientId, ChatClient chatClient);

    OpenAiApi getRequiredApi(String apiId);

    ChatModel getRequiredModel(String modelId);

    Advisor getRequiredAdvisor(String advisorId);

    ChatClient getRequiredChatClient(String clientId);
}
