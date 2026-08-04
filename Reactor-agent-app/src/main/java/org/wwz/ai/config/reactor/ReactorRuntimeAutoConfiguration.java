package org.wwz.ai.config.reactor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.TaskScheduler;
import org.wwz.ai.domain.agent.adapter.port.FileArtifactPort;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpPort;
import org.wwz.ai.domain.agent.adapter.port.RemoteStreamPort;
import org.wwz.ai.domain.agent.runtime.llm.DomainMessageConverter;
import org.wwz.ai.domain.agent.runtime.llm.LlmChatModelResolver;
import org.wwz.ai.domain.agent.runtime.llm.LlmChatResponseMapper;
import org.wwz.ai.domain.agent.runtime.llm.OpenAiChatOptionsFactory;
import org.wwz.ai.domain.agent.runtime.llm.StreamResponseHandler;
import org.wwz.ai.domain.agent.runtime.tool.mcp.runtime.McpToolExecutor;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.runtime.ReactorLlmDependencies;
import org.wwz.ai.domain.agent.memory.SessionContextCompactionService;
import org.wwz.ai.domain.agent.memory.ltm.BackgroundReviewService;
import org.wwz.ai.domain.agent.memory.ltm.CuratedMemoryStore;
import org.wwz.ai.domain.agent.memory.ltm.LtmManager;
import org.wwz.ai.domain.agent.memory.ltm.MemoryFlushService;
import org.wwz.ai.domain.agent.memory.ltm.SessionSearchService;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.springframework.beans.factory.ObjectProvider;
import org.wwz.ai.domain.agent.reactor.service.imagegeneration.IImageGenerationExecutionKernel;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentConcurrencyGate;
import org.wwz.ai.types.agent.config.AgentExecutorNames;
import org.wwz.ai.types.agent.config.AgentExecutorProperties;

import java.util.concurrent.Executor;

/**
 * Reactor 运行时依赖装配。
 * app 负责把 Spring Bean 组装为 domain 可消费的 typed runtime bundle。
 * 不在此处承接执行编排、controller 协议适配。
 */
@Configuration
public class ReactorRuntimeAutoConfiguration {

    @Bean
    public ReactorLlmDependencies reactorLlmDependencies(LlmChatModelResolver chatModelResolver,
                                                         OpenAiChatOptionsFactory chatOptionsFactory,
                                                         DomainMessageConverter messageConverter,
                                                         LlmChatResponseMapper responseMapper,
                                                         StreamResponseHandler streamResponseHandler) {
        return ReactorLlmDependencies.builder()
                .chatModelResolver(chatModelResolver)
                .chatOptionsFactory(chatOptionsFactory)
                .messageConverter(messageConverter)
                .responseMapper(responseMapper)
                .streamResponseHandler(streamResponseHandler)
                .build();
    }

    @Bean
    public ReactorRuntimeDependencies reactorRuntimeDependencies(ReactorConfig reactorConfig,
                                                                 Environment environment,
                                                                 ReactorLlmDependencies reactorLlmDependencies,
                                                                 McpToolExecutor mcpToolExecutor,
                                                                 IImageGenerationExecutionKernel imageGenerationExecutionKernel,
                                                                 RemoteHttpPort remoteHttpPort,
                                                                 RemoteStreamPort remoteStreamPort,
                                                                 FileArtifactPort fileArtifactPort,
                                                                  @Qualifier(AgentExecutorNames.LLM_EXECUTOR) Executor llmExecutor,
                                                                  @Qualifier(AgentExecutorNames.TASK_EXECUTOR) Executor taskExecutor,
                                                                  @Qualifier(AgentExecutorNames.TOOL_EXECUTOR) Executor toolExecutor,
                                                                  @Qualifier(AgentExecutorNames.HEARTBEAT_SCHEDULER) TaskScheduler heartbeatScheduler,
                                                                   @Lazy SessionContextCompactionService sessionContextCompactionService,
                                                                   ObjectProvider<LtmManager> ltmManagerProvider,
                                                                   ObjectProvider<CuratedMemoryStore> curatedMemoryStoreProvider,
                                                                   ObjectProvider<SessionSearchService> sessionSearchServiceProvider,
                                                                   ObjectProvider<MemoryFlushService> memoryFlushServiceProvider,
                                                                   ObjectProvider<BackgroundReviewService> backgroundReviewServiceProvider,
                                                                   ObjectProvider<LtmProperties> ltmPropertiesProvider,
                                                                   AgentExecutorProperties agentExecutorProperties) {
        // SessionContextCompactionService 是接口，@Lazy 可走 JDK 代理；
        // 反向依赖用 ObjectProvider，避免对 final 的 ReactorRuntimeDependencies 做 CGLIB 代理。
        return ReactorRuntimeDependencies.builder()
                .reactorConfig(reactorConfig)
                .environment(environment)
                .llmDependencies(reactorLlmDependencies)
                .mcpToolExecutor(mcpToolExecutor)
                .imageGenerationExecutionKernel(imageGenerationExecutionKernel)
                .remoteHttpPort(remoteHttpPort)
                .remoteStreamPort(remoteStreamPort)
                .fileArtifactPort(fileArtifactPort)
                .llmExecutor(llmExecutor)
                .taskExecutor(taskExecutor)
                .toolExecutor(toolExecutor)
                .heartbeatScheduler(heartbeatScheduler)
                .toolBatchTimeoutSeconds(agentExecutorProperties.getToolBatchTimeoutSeconds())
                .sessionContextCompactionService(sessionContextCompactionService)
                .ltmManager(ltmManagerProvider.getIfAvailable())
                .curatedMemoryStore(curatedMemoryStoreProvider.getIfAvailable())
                .sessionSearchService(sessionSearchServiceProvider.getIfAvailable())
                .memoryFlushService(memoryFlushServiceProvider.getIfAvailable())
                .backgroundReviewService(backgroundReviewServiceProvider.getIfAvailable())
                .ltmFlushMinTurns(ltmPropertiesProvider.getIfAvailable() == null
                        ? 6
                        : ltmPropertiesProvider.getIfAvailable().getFlushMinTurns())
                .build();
    }

    @Bean
    public SubAgentConcurrencyGate subAgentConcurrencyGate(AgentExecutorProperties agentExecutorProperties) {
        int max = agentExecutorProperties.getMaxConcurrentSubAgents() == null
                ? SubAgentConcurrencyGate.DEFAULT_MAX_CONCURRENT
                : agentExecutorProperties.getMaxConcurrentSubAgents();
        long acquireTimeout = agentExecutorProperties.getSubAgentAcquireTimeoutSeconds() == null
                ? SubAgentConcurrencyGate.DEFAULT_ACQUIRE_TIMEOUT_SECONDS
                : agentExecutorProperties.getSubAgentAcquireTimeoutSeconds();
        return new SubAgentConcurrencyGate(max, acquireTimeout);
    }
}
