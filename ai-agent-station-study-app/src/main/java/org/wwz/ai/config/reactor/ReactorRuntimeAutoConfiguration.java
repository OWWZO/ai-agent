package org.wwz.ai.config.reactor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.wwz.ai.domain.agent.reactor.agent.llm.DomainMessageConverter;
import org.wwz.ai.domain.agent.reactor.agent.llm.LlmChatModelResolver;
import org.wwz.ai.domain.agent.reactor.agent.llm.LlmChatResponseMapper;
import org.wwz.ai.domain.agent.reactor.agent.llm.OpenAiChatOptionsFactory;
import org.wwz.ai.domain.agent.reactor.agent.llm.StreamResponseHandler;
import org.wwz.ai.domain.agent.reactor.agent.tool.mcp.runtime.McpToolExecutor;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.reactor.runtime.ReactorLlmDependencies;
import org.wwz.ai.domain.agent.reactor.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.reactor.service.imagegeneration.IImageGenerationExecutionKernel;

/**
 * Reactor 运行时依赖装配。
 * app 负责把 Spring Bean 组装成 domain 可消费的 typed runtime bundle。
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
                                                                 IImageGenerationExecutionKernel imageGenerationExecutionKernel) {
        return ReactorRuntimeDependencies.builder()
                .reactorConfig(reactorConfig)
                .environment(environment)
                .llmDependencies(reactorLlmDependencies)
                .mcpToolExecutor(mcpToolExecutor)
                .imageGenerationExecutionKernel(imageGenerationExecutionKernel)
                .build();
    }
}
