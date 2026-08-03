package org.wwz.ai.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import lombok.extern.slf4j.Slf4j;
import org.wwz.ai.config.executor.BoundedVirtualThreadExecutor;
import org.wwz.ai.config.executor.CountingRejectedExecutionHandler;
import org.wwz.ai.types.agent.config.AgentExecutorNames;
import org.wwz.ai.types.agent.config.AgentExecutorProperties;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Agent 主链路执行器装配。
 */
@Configuration
@EnableConfigurationProperties(AgentExecutorProperties.class)
@Slf4j
public class AgentExecutorConfiguration {

    @Bean(name = AgentExecutorNames.DISPATCH_EXECUTOR, destroyMethod = "shutdown")
    public Executor agentDispatchExecutor(AgentExecutorProperties properties) {
        return buildExecutor("dispatch", properties.getDispatch(), properties);
    }

    @Bean(name = AgentExecutorNames.LLM_EXECUTOR, destroyMethod = "shutdown")
    public Executor agentLlmExecutor(AgentExecutorProperties properties) {
        return buildExecutor("llm", properties.getLlm(), properties);
    }

    @Bean(name = AgentExecutorNames.TASK_EXECUTOR, destroyMethod = "shutdown")
    public Executor agentTaskExecutor(AgentExecutorProperties properties) {
        return buildExecutor("task", properties.getTask(), properties);
    }

    @Bean(name = AgentExecutorNames.TOOL_EXECUTOR, destroyMethod = "shutdown")
    public Executor agentToolExecutor(AgentExecutorProperties properties) {
        return buildExecutor("tool", properties.getTool(), properties);
    }

    @Bean(name = AgentExecutorNames.HEARTBEAT_SCHEDULER)
    public TaskScheduler agentHeartbeatScheduler(AgentExecutorProperties properties) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(properties.getHeartbeat().getPoolSize());
        scheduler.setThreadNamePrefix(properties.getHeartbeat().getThreadNamePrefix());
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.setRejectedExecutionHandler(resolveRejectedExecutionHandler("AbortPolicy"));
        scheduler.initialize();
        return scheduler;
    }

    /**
     * 向 legacy armory 装配链暴露统一的受控工具执行器，避免继续回退到匿名线程池。
     * 修复虚拟线程开关与 legacy bridge 条件不一致的问题。
     */
    @Bean
    @ConditionalOnProperty(
            name = {
                    "autobots.execution.tool.virtual-threads-enabled",
                    "autobots.execution.virtual-threads-enabled"
            },
            havingValue = "false",
            matchIfMissing = true)
    public ThreadPoolExecutor threadPoolExecutor(@Qualifier(AgentExecutorNames.TOOL_EXECUTOR) Executor executor) {
        if (executor instanceof ThreadPoolTaskExecutor taskExecutor) {
            return taskExecutor.getThreadPoolExecutor();
        }
        throw new IllegalStateException("legacy thread pool bridge requires platform tool executor");
    }

    private Executor buildExecutor(String name,
                                   AgentExecutorProperties.Pool pool,
                                   AgentExecutorProperties properties) {
        // dispatch/llm 主要等待模型或远端响应；tool/task 主要承接阻塞 I/O。
        // heartbeat 是调度器工作，始终保留平台线程，避免把周期任务混入虚拟线程准入预算。
        if (properties.isVirtualThreadsEnabled() && pool.isVirtualThreadsEnabled()) {
            int maxConcurrency = resolveMaxConcurrency(pool);
            return new BoundedVirtualThreadExecutor(name, maxConcurrency, pool.getThreadNamePrefix());
        }
        return buildPlatformExecutor(name, pool);
    }

    private ThreadPoolTaskExecutor buildPlatformExecutor(String name, AgentExecutorProperties.Pool pool) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(pool.getCorePoolSize());
        executor.setMaxPoolSize(pool.getMaxPoolSize());
        executor.setQueueCapacity(pool.getQueueCapacity());
        executor.setKeepAliveSeconds(Math.toIntExact(pool.getKeepAliveSeconds()));
        executor.setThreadNamePrefix(pool.getThreadNamePrefix());
        executor.setRejectedExecutionHandler(new CountingRejectedExecutionHandler(
                resolveRejectedExecutionHandler(pool.getRejectPolicy())));
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        log.info("Agent executor started: name={}, mode=platform, capacity={}, queueCapacity={}, rejection={}",
                name, pool.getMaxPoolSize(), pool.getQueueCapacity(), pool.getRejectPolicy());
        return executor;
    }

    private int resolveMaxConcurrency(AgentExecutorProperties.Pool pool) {
        Integer configured = pool.getMaxConcurrency();
        if (configured != null && configured > 0) {
            return configured;
        }
        return Math.max(1, pool.getMaxPoolSize());
    }

    private RejectedExecutionHandler resolveRejectedExecutionHandler(String policy) {
        if ("DiscardPolicy".equals(policy)) {
            return new ThreadPoolExecutor.DiscardPolicy();
        }
        if ("DiscardOldestPolicy".equals(policy)) {
            return new ThreadPoolExecutor.DiscardOldestPolicy();
        }
        if ("CallerRunsPolicy".equals(policy)) {
            return new ThreadPoolExecutor.CallerRunsPolicy();
        }
        if ("AbortPolicy".equals(policy)) {
            return new ThreadPoolExecutor.AbortPolicy();
        }
        return new ThreadPoolExecutor.AbortPolicy();
    }
}
