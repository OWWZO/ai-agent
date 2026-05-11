package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatModel;
import org.wwz.ai.domain.agent.service.runtime.AiClientRuntimeRegistry;
import org.wwz.ai.domain.agent.service.runtime.InMemoryAiClientRuntimeRegistry;

import static org.mockito.Mockito.mock;

/**
 * AI Client 运行时注册表测试。
 * 锁定显式注册/解析行为，避免运行时再次回退到 Spring Bean 名称查找。
 */
public class AiClientRuntimeRegistryTest {

    @Test
    public void shouldRegisterAndResolveRuntimeCollaboratorsByBusinessId() {
        AiClientRuntimeRegistry registry = new InMemoryAiClientRuntimeRegistry();
        Object openAiApi = mock(org.springframework.ai.openai.api.OpenAiApi.class);
        ChatModel chatModel = mock(ChatModel.class);
        Advisor advisor = mock(Advisor.class);
        ChatClient chatClient = mock(ChatClient.class);

        registry.registerApi("api-1", (org.springframework.ai.openai.api.OpenAiApi) openAiApi);
        registry.registerModel("model-1", chatModel);
        registry.registerAdvisor("advisor-1", advisor);
        registry.registerChatClient("client-1", chatClient);

        Assert.assertSame(openAiApi, registry.getRequiredApi("api-1"));
        Assert.assertSame(chatModel, registry.getRequiredModel("model-1"));
        Assert.assertSame(advisor, registry.getRequiredAdvisor("advisor-1"));
        Assert.assertSame(chatClient, registry.getRequiredChatClient("client-1"));
    }

    @Test
    public void shouldFailFastWhenRuntimeCollaboratorIsMissing() {
        AiClientRuntimeRegistry registry = new InMemoryAiClientRuntimeRegistry();

        IllegalStateException apiError = Assert.assertThrows(
                IllegalStateException.class,
                () -> registry.getRequiredApi("missing-api"));
        IllegalStateException modelError = Assert.assertThrows(
                IllegalStateException.class,
                () -> registry.getRequiredModel("missing-model"));
        IllegalStateException advisorError = Assert.assertThrows(
                IllegalStateException.class,
                () -> registry.getRequiredAdvisor("missing-advisor"));
        IllegalStateException clientError = Assert.assertThrows(
                IllegalStateException.class,
                () -> registry.getRequiredChatClient("missing-client"));

        Assert.assertTrue(apiError.getMessage().contains("missing-api"));
        Assert.assertTrue(modelError.getMessage().contains("missing-model"));
        Assert.assertTrue(advisorError.getMessage().contains("missing-advisor"));
        Assert.assertTrue(clientError.getMessage().contains("missing-client"));
    }
}
