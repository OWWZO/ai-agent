package org.wwz.ai.config.executor;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.wwz.ai.types.agent.config.AgentExecutorNames;

import java.util.concurrent.Executor;

/**
 * Agent 执行器 Micrometer 观测装配。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(MeterRegistry.class)
public class AgentExecutorMetricsConfiguration {

    @Bean
    public AgentExecutorMetrics agentExecutorMetrics(
            MeterRegistry meterRegistry,
            @Qualifier(AgentExecutorNames.DISPATCH_EXECUTOR) Executor dispatchExecutor,
             @Qualifier(AgentExecutorNames.LLM_EXECUTOR) Executor llmExecutor,
             @Qualifier(AgentExecutorNames.TASK_EXECUTOR) Executor taskExecutor,
             @Qualifier(AgentExecutorNames.TOOL_EXECUTOR) Executor toolExecutor) {
        return new AgentExecutorMetrics(
                meterRegistry,
                dispatchExecutor,
                llmExecutor,
                taskExecutor,
                toolExecutor
        );
    }
}
