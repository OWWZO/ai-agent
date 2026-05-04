package org.wwz.ai.test.domain.support;

import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.domain.agent.reactor.agent.llm.DomainMessageConverter;
import org.wwz.ai.domain.agent.reactor.agent.llm.LlmChatModelResolver;
import org.wwz.ai.domain.agent.reactor.agent.llm.LlmChatResponseMapper;
import org.wwz.ai.domain.agent.reactor.agent.llm.LlmToolCallbackProvider;
import org.wwz.ai.domain.agent.reactor.agent.llm.OpenAiChatOptionsFactory;
import org.wwz.ai.domain.agent.reactor.agent.llm.StreamResponseHandler;
import org.wwz.ai.domain.agent.reactor.agent.tool.mcp.runtime.McpRegistry;
import org.wwz.ai.domain.agent.reactor.agent.tool.mcp.runtime.McpToolExecutor;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.reactor.runtime.ReactorLlmDependencies;
import org.wwz.ai.domain.agent.reactor.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.reactor.service.imagegeneration.IImageGenerationExecutionKernel;

/**
 * Reactor 运行时测试夹具。
 * 统一为单测构造最小可用的 ReactorRuntimeDependencies，避免测试回退到全局 Spring 上下文。
 */
public final class ReactorRuntimeTestSupport {

    private ReactorRuntimeTestSupport() {
    }

    public static ReactorRuntimeDependencies runtimeDependencies(ReactorConfig reactorConfig) {
        return runtimeDependencies(reactorConfig, null, new MockEnvironment());
    }

    public static ReactorRuntimeDependencies runtimeDependencies(ReactorConfig reactorConfig,
                                                                 IImageGenerationExecutionKernel imageKernel) {
        return runtimeDependencies(reactorConfig, imageKernel, new MockEnvironment());
    }

    public static ReactorRuntimeDependencies runtimeDependencies(ReactorConfig reactorConfig,
                                                                 IImageGenerationExecutionKernel imageKernel,
                                                                 Environment environment) {
        DomainMessageConverter messageConverter = new DomainMessageConverter();
        ReflectionTestUtils.setField(messageConverter, "reactorConfig", reactorConfig);

        LlmToolCallbackProvider toolCallbackProvider = new LlmToolCallbackProvider();
        ReflectionTestUtils.setField(toolCallbackProvider, "mcpRegistry", org.mockito.Mockito.mock(McpRegistry.class));

        OpenAiChatOptionsFactory chatOptionsFactory = new OpenAiChatOptionsFactory();
        ReflectionTestUtils.setField(chatOptionsFactory, "toolCallbackProvider", toolCallbackProvider);

        StreamResponseHandler streamResponseHandler = new StreamResponseHandler();
        LlmChatResponseMapper responseMapper = new LlmChatResponseMapper();
        ReflectionTestUtils.setField(streamResponseHandler, "reactorConfig", reactorConfig);
        ReflectionTestUtils.setField(streamResponseHandler, "chatResponseMapper", responseMapper);
        ReactorLlmDependencies llmDependencies = ReactorLlmDependencies.builder()
                .chatModelResolver(new LlmChatModelResolver())
                .chatOptionsFactory(chatOptionsFactory)
                .messageConverter(messageConverter)
                .responseMapper(responseMapper)
                .streamResponseHandler(streamResponseHandler)
                .build();

        return ReactorRuntimeDependencies.builder()
                .reactorConfig(reactorConfig)
                .environment(environment)
                .llmDependencies(llmDependencies)
                .mcpToolExecutor(null)
                .imageGenerationExecutionKernel(imageKernel)
                .build();
    }
}
