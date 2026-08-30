package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.config.AgentExecutorConfiguration;
import org.wwz.ai.config.executor.BoundedVirtualThreadExecutor;
import org.wwz.ai.config.executor.CountingRejectedExecutionHandler;
import org.wwz.ai.types.agent.config.AgentExecutorNames;
import org.wwz.ai.types.agent.config.AgentExecutorProperties;
import org.wwz.ai.test.domain.support.ReactorRuntimeTestSupport;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Agent 执行器装配测试。
 */
public class AgentExecutorConfigurationTest {

    @Test
    public void shouldExposeNamedManagedExecutorsAndScheduler() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(AgentExecutorConfiguration.class);

        try {
            context.refresh();

            Executor dispatchExecutor = context.getBean(AgentExecutorNames.DISPATCH_EXECUTOR, Executor.class);
            Executor llmExecutor = context.getBean(AgentExecutorNames.LLM_EXECUTOR, Executor.class);
            ThreadPoolTaskExecutor taskExecutor = context.getBean(AgentExecutorNames.TASK_EXECUTOR, ThreadPoolTaskExecutor.class);
            ThreadPoolTaskExecutor toolExecutor = context.getBean(AgentExecutorNames.TOOL_EXECUTOR, ThreadPoolTaskExecutor.class);
            TaskScheduler heartbeatScheduler = context.getBean(AgentExecutorNames.HEARTBEAT_SCHEDULER, TaskScheduler.class);
            ThreadPoolExecutor legacyBridgeExecutor = context.getBean("threadPoolExecutor", ThreadPoolExecutor.class);

            Assert.assertNotNull(dispatchExecutor);
            Assert.assertNotNull(llmExecutor);
            Assert.assertNotNull(taskExecutor);
            Assert.assertNotNull(toolExecutor);
            Assert.assertNotNull(heartbeatScheduler);
            Assert.assertNotSame(taskExecutor.getThreadPoolExecutor(), toolExecutor.getThreadPoolExecutor());
            Assert.assertSame(toolExecutor.getThreadPoolExecutor(), legacyBridgeExecutor);
        } finally {
            context.close();
        }
    }

    @Test
    public void shouldUseAbortPolicyByDefault() {
        AgentExecutorProperties properties = new AgentExecutorProperties();
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) new AgentExecutorConfiguration()
                .agentToolExecutor(properties);

        Assert.assertTrue(executor.getThreadPoolExecutor().getRejectedExecutionHandler()
                instanceof CountingRejectedExecutionHandler);
        Assert.assertTrue(((CountingRejectedExecutionHandler) executor.getThreadPoolExecutor()
                .getRejectedExecutionHandler()).getDelegate() instanceof ThreadPoolExecutor.AbortPolicy);
    }

    @Test
    public void shouldHonorConfiguredCallerRunsPolicy() {
        AgentExecutorProperties properties = new AgentExecutorProperties();
        properties.getTool().setRejectPolicy("CallerRunsPolicy");

        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) new AgentExecutorConfiguration()
                .agentToolExecutor(properties);

        Assert.assertTrue(executor.getThreadPoolExecutor().getRejectedExecutionHandler()
                instanceof CountingRejectedExecutionHandler);
        Assert.assertTrue(((CountingRejectedExecutionHandler) executor.getThreadPoolExecutor()
                .getRejectedExecutionHandler()).getDelegate() instanceof ThreadPoolExecutor.CallerRunsPolicy);
    }

    @Test
    public void shouldEnableVirtualExecutorOnlyForExplicitlySelectedPool() {
        AgentExecutorProperties properties = new AgentExecutorProperties();
        properties.setVirtualThreadsEnabled(true);
        properties.getDispatch().setVirtualThreadsEnabled(true);

        Executor dispatch = new AgentExecutorConfiguration().agentDispatchExecutor(properties);
        Executor llm = new AgentExecutorConfiguration().agentLlmExecutor(properties);
        try {
            Assert.assertTrue(dispatch instanceof BoundedVirtualThreadExecutor);
            Assert.assertTrue(llm instanceof ThreadPoolTaskExecutor);
        } finally {
            ((BoundedVirtualThreadExecutor) dispatch).shutdown();
            ((ThreadPoolTaskExecutor) llm).shutdown();
        }
    }

    @Test
    public void shouldMigrateIoPoolsIndependentlyAndKeepHeartbeatOnPlatformThreads() {
        AgentExecutorProperties properties = new AgentExecutorProperties();
        properties.setVirtualThreadsEnabled(true);
        properties.getDispatch().setVirtualThreadsEnabled(true);
        properties.getLlm().setVirtualThreadsEnabled(true);
        properties.getTask().setVirtualThreadsEnabled(true);
        properties.getTool().setVirtualThreadsEnabled(true);
        properties.getTask().setMaxConcurrency(3);
        properties.getTool().setMaxConcurrency(2);

        AgentExecutorConfiguration configuration = new AgentExecutorConfiguration();
        Executor dispatch = configuration.agentDispatchExecutor(properties);
        Executor llm = configuration.agentLlmExecutor(properties);
        Executor task = configuration.agentTaskExecutor(properties);
        Executor tool = configuration.agentToolExecutor(properties);
        TaskScheduler heartbeat = configuration.agentHeartbeatScheduler(properties);
        try {
            Assert.assertTrue(dispatch instanceof BoundedVirtualThreadExecutor);
            Assert.assertTrue(llm instanceof BoundedVirtualThreadExecutor);
            Assert.assertEquals(3, ((BoundedVirtualThreadExecutor) task).getMaxConcurrency());
            Assert.assertEquals(2, ((BoundedVirtualThreadExecutor) tool).getMaxConcurrency());
            Assert.assertTrue(task instanceof BoundedVirtualThreadExecutor);
            Assert.assertTrue(tool instanceof BoundedVirtualThreadExecutor);
            Assert.assertTrue(heartbeat instanceof ThreadPoolTaskScheduler);
        } finally {
            ((BoundedVirtualThreadExecutor) dispatch).shutdown();
            ((BoundedVirtualThreadExecutor) llm).shutdown();
            ((BoundedVirtualThreadExecutor) task).shutdown();
            ((BoundedVirtualThreadExecutor) tool).shutdown();
            ((ThreadPoolTaskScheduler) heartbeat).shutdown();
        }
    }

    @Test
    public void shouldExposeTaskExecutorDefaultsThroughRuntimeFixture() {
        AgentExecutorProperties properties = new AgentExecutorProperties();
        Assert.assertNotNull(properties.getTask());
        Assert.assertEquals("agent-task-", properties.getTask().getThreadNamePrefix());

        ReactorConfig reactorConfig = new ReactorConfig();
        ReactorRuntimeDependencies runtimeDependencies = ReactorRuntimeTestSupport.runtimeDependencies(reactorConfig);

        Assert.assertNotNull(runtimeDependencies.requireReactorConfig());
        Assert.assertNotNull(runtimeDependencies.requireTaskExecutor());
    }

}
