package org.wwz.ai.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
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
public class AgentExecutorConfiguration {

    @Bean(name = AgentExecutorNames.DISPATCH_EXECUTOR)
    public Executor agentDispatchExecutor(AgentExecutorProperties properties) {
        return buildExecutor(properties.getDispatch());
    }

    @Bean(name = AgentExecutorNames.LLM_EXECUTOR)
    public Executor agentLlmExecutor(AgentExecutorProperties properties) {
        return buildExecutor(properties.getLlm());
    }

    @Bean(name = AgentExecutorNames.TASK_EXECUTOR)
    public ThreadPoolTaskExecutor agentTaskExecutor(AgentExecutorProperties properties) {
        return buildExecutor(properties.getTask());
    }

    @Bean(name = AgentExecutorNames.TOOL_EXECUTOR)
    public ThreadPoolTaskExecutor agentToolExecutor(AgentExecutorProperties properties) {
        return buildExecutor(properties.getTool());
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
     */
    @Bean
    public ThreadPoolExecutor threadPoolExecutor(@Qualifier(AgentExecutorNames.TOOL_EXECUTOR) ThreadPoolTaskExecutor executor) {
        return executor.getThreadPoolExecutor();
    }

    private ThreadPoolTaskExecutor buildExecutor(AgentExecutorProperties.Pool pool) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(pool.getCorePoolSize());
        executor.setMaxPoolSize(pool.getMaxPoolSize());
        executor.setQueueCapacity(pool.getQueueCapacity());
        executor.setKeepAliveSeconds(Math.toIntExact(pool.getKeepAliveSeconds()));
        executor.setThreadNamePrefix(pool.getThreadNamePrefix());
        executor.setRejectedExecutionHandler(resolveRejectedExecutionHandler(pool.getRejectPolicy()));
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
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
        return new ThreadPoolExecutor.AbortPolicy();
    }
}
