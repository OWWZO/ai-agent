package org.wwz.ai.test.domain.support;

import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.domain.agent.adapter.port.FileArtifactPort;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpPort;
import org.wwz.ai.domain.agent.adapter.port.RemoteStreamPort;
import org.wwz.ai.domain.agent.runtime.llm.LlmCompletionPort;
import org.wwz.ai.domain.agent.runtime.llm.LlmResponse;
import org.wwz.ai.domain.agent.runtime.llm.LlmStreamEvent;
import org.wwz.ai.domain.agent.runtime.llm.StreamResponseHandler;
import org.wwz.ai.domain.agent.runtime.tool.mcp.port.McpToolExecutor;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.runtime.ReactorLlmDependencies;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.image.service.IImageGenerationExecutionKernel;
import org.wwz.ai.infrastructure.adapter.port.OkHttpRemoteHttpAdapter;
import org.wwz.ai.infrastructure.adapter.port.OkHttpRemoteStreamAdapter;
import org.wwz.ai.infrastructure.adapter.port.ReactorToolFileArtifactAdapter;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * Reactor 运行时测试夹具。
 * 统一为单测构造最小可用的 ReactorRuntimeDependencies，避免测试回退到全局 Spring 上下文。
 */
public final class ReactorRuntimeTestSupport {

    private ReactorRuntimeTestSupport() {
    }

    public static ReactorRuntimeDependencies runtimeDependencies(ReactorConfig reactorConfig) {
        return runtimeDependencies(reactorConfig, null, new MockEnvironment(), null);
    }

    public static ReactorRuntimeDependencies runtimeDependencies(ReactorConfig reactorConfig,
                                                                  RemoteHttpPort remoteHttpPort) {
        return runtimeDependencies(reactorConfig, null, new MockEnvironment(), remoteHttpPort);
    }

    public static ReactorRuntimeDependencies runtimeDependencies(ReactorConfig reactorConfig,
                                                                  IImageGenerationExecutionKernel imageKernel) {
        return runtimeDependencies(reactorConfig, imageKernel, new MockEnvironment(), null);
    }

    public static ReactorRuntimeDependencies runtimeDependencies(ReactorConfig reactorConfig,
                                                                  IImageGenerationExecutionKernel imageKernel,
                                                                  Environment environment) {
        return runtimeDependencies(reactorConfig, imageKernel, environment, null);
    }

    public static ReactorRuntimeDependencies runtimeDependencies(ReactorConfig reactorConfig,
                                                                  IImageGenerationExecutionKernel imageKernel,
                                                                  Environment environment,
                                                                  RemoteHttpPort overrideRemoteHttpPort) {
        StreamResponseHandler streamResponseHandler = new StreamResponseHandler();
        ReflectionTestUtils.setField(streamResponseHandler, "reactorConfig", reactorConfig);
        ReactorLlmDependencies llmDependencies = ReactorLlmDependencies.builder()
                .completionPort(new TestLlmCompletionPort())
                .streamResponseHandler(streamResponseHandler)
                .build();
        RemoteHttpPort remoteHttpPort = overrideRemoteHttpPort != null
                ? overrideRemoteHttpPort
                : new OkHttpRemoteHttpAdapter();
        RemoteStreamPort remoteStreamPort = new OkHttpRemoteStreamAdapter();
        FileArtifactPort fileArtifactPort = new ReactorToolFileArtifactAdapter(
                overrideRemoteHttpPort != null ? overrideRemoteHttpPort : new OkHttpRemoteHttpAdapter());
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

    private static final class TestLlmCompletionPort implements LlmCompletionPort {

        @Override
        public LlmResponse complete(org.wwz.ai.domain.agent.runtime.llm.LlmRequest request) {
            return LlmResponse.builder().content("test response").build();
        }

        @Override
        public StreamCall stream(org.wwz.ai.domain.agent.runtime.llm.LlmRequest request) {
            return new StreamCall() {
                @Override
                public void subscribe(java.util.function.Consumer<LlmStreamEvent> onEvent,
                                      java.util.function.Consumer<Throwable> onError,
                                      Runnable onComplete) {
                    onEvent.accept(LlmStreamEvent.builder().content("test response").build());
                    onComplete.run();
                }

                @Override
                public void cancel() {
                }
            };
        }
    }
}
