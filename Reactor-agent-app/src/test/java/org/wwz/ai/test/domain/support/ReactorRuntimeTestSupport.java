package org.wwz.ai.test.domain.support;

import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.domain.agent.adapter.port.FileArtifactPort;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpPort;
import org.wwz.ai.domain.agent.adapter.port.RemoteStreamPort;
import org.wwz.ai.domain.agent.runtime.llm.DomainMessageConverter;
import org.wwz.ai.domain.agent.runtime.llm.LlmChatModelResolver;
import org.wwz.ai.domain.agent.runtime.llm.LlmChatResponseMapper;
import org.wwz.ai.domain.agent.runtime.llm.LlmToolCallbackProvider;
import org.wwz.ai.domain.agent.runtime.llm.OpenAiChatOptionsFactory;
import org.wwz.ai.domain.agent.runtime.llm.StreamResponseHandler;
import org.wwz.ai.domain.agent.runtime.tool.mcp.runtime.McpRegistry;
import org.wwz.ai.domain.agent.runtime.tool.mcp.runtime.McpToolExecutor;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.runtime.ReactorLlmDependencies;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.reactor.service.imagegeneration.IImageGenerationExecutionKernel;
import org.wwz.ai.infrastructure.adapter.port.OkHttpRemoteHttpAdapter;
import org.wwz.ai.infrastructure.adapter.port.OkHttpRemoteStreamAdapter;
import org.wwz.ai.infrastructure.adapter.port.ReactorToolFileArtifactAdapter;

import java.util.concurrent.Executor;

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
        RemoteHttpPort remoteHttpPort = new OkHttpRemoteHttpAdapter();
        RemoteStreamPort remoteStreamPort = new OkHttpRemoteStreamAdapter();
        FileArtifactPort fileArtifactPort = new ReactorToolFileArtifactAdapter(remoteHttpPort);
        Executor sameThreadExecutor = Runnable::run;

        return ReactorRuntimeDependencies.builder()
                .reactorConfig(reactorConfig)
                .environment(environment)
                .llmDependencies(llmDependencies)
                .mcpToolExecutor(null)
                .imageGenerationExecutionKernel(imageKernel)
                .remoteHttpPort(remoteHttpPort)
                .remoteStreamPort(remoteStreamPort)
                .fileArtifactPort(fileArtifactPort)
                .llmExecutor(sameThreadExecutor)
                .taskExecutor(sameThreadExecutor)
                .toolExecutor(sameThreadExecutor)
                .heartbeatScheduler(new ConcurrentTaskScheduler())
                .build();
    }
}
