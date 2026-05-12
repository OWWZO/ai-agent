package org.wwz.ai.domain.agent.service.runtime;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于内存缓存的 AI Client 运行时注册表实现。
 */
@Component
public class InMemoryAiClientRuntimeRegistry implements AiClientRuntimeRegistry {

    private final Map<String, OpenAiApi> apiRegistry = new ConcurrentHashMap<>();
    private final Map<String, ChatModel> modelRegistry = new ConcurrentHashMap<>();
    private final Map<String, Advisor> advisorRegistry = new ConcurrentHashMap<>();
    private final Map<String, ChatClient> chatClientRegistry = new ConcurrentHashMap<>();

    @Override
    public void registerApi(String apiId, OpenAiApi openAiApi) {
        apiRegistry.put(apiId, openAiApi);
    }

    @Override
    public void registerModel(String modelId, ChatModel chatModel) {
        modelRegistry.put(modelId, chatModel);
    }

    @Override
    public void registerAdvisor(String advisorId, Advisor advisor) {
        advisorRegistry.put(advisorId, advisor);
    }

    @Override
    public void registerChatClient(String clientId, ChatClient chatClient) {
        chatClientRegistry.put(clientId, chatClient);
    }

    @Override
    public OpenAiApi getRequiredApi(String apiId) {
        OpenAiApi openAiApi = apiRegistry.get(apiId);
        if (openAiApi == null) {
            throw new IllegalStateException("未找到 API 运行时配置: " + apiId);
        }
        return openAiApi;
    }

    @Override
    public ChatModel getRequiredModel(String modelId) {
        ChatModel chatModel = modelRegistry.get(modelId);
        if (chatModel == null) {
            throw new IllegalStateException("未找到模型运行时配置: " + modelId);
        }
        return chatModel;
    }

    @Override
    public Advisor getRequiredAdvisor(String advisorId) {
        Advisor advisor = advisorRegistry.get(advisorId);
        if (advisor == null) {
            throw new IllegalStateException("未找到顾问运行时配置: " + advisorId);
        }
        return advisor;
    }

    @Override
    public ChatClient getRequiredChatClient(String clientId) {
        ChatClient chatClient = chatClientRegistry.get(clientId);
        if (chatClient == null) {
            throw new IllegalStateException("未找到客户端运行时配置: " + clientId);
        }
        return chatClient;
    }
}
