package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.config.AgentExecutorConfiguration;
import org.wwz.ai.types.agent.config.AgentExecutorNames;
import org.wwz.ai.types.agent.config.AgentExecutorProperties;
import org.wwz.ai.types.job.config.TaskJobAutoConfig;
import org.wwz.ai.types.job.service.ITaskJobService;
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

        Assert.assertTrue(executor.getThreadPoolExecutor().getRejectedExecutionHandler() instanceof ThreadPoolExecutor.AbortPolicy);
    }

    @Test
    public void shouldHonorConfiguredCallerRunsPolicy() {
        AgentExecutorProperties properties = new AgentExecutorProperties();
        properties.getTool().setRejectPolicy("CallerRunsPolicy");

        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) new AgentExecutorConfiguration()
                .agentToolExecutor(properties);

        Assert.assertTrue(executor.getThreadPoolExecutor().getRejectedExecutionHandler() instanceof ThreadPoolExecutor.CallerRunsPolicy);
    }

    @Test
    public void shouldExposeTaskExecutorDefaultsAndPlannerParallelismThroughRuntimeFixture() {
        AgentExecutorProperties properties = new AgentExecutorProperties();
        Assert.assertNotNull(properties.getTask());
        Assert.assertEquals("agent-task-", properties.getTask().getThreadNamePrefix());

        ReactorConfig reactorConfig = new ReactorConfig();
        ReflectionTestUtils.setField(reactorConfig, "plannerMaxParallelTasks", 3);

        ReactorRuntimeDependencies runtimeDependencies = ReactorRuntimeTestSupport.runtimeDependencies(reactorConfig);

        Assert.assertEquals(Integer.valueOf(3), runtimeDependencies.requireReactorConfig().getPlannerMaxParallelTasks());
        Assert.assertNotNull(runtimeDependencies.requireTaskExecutor());
    }

    @Test
    public void shouldWireTaskJobServiceToNamedTaskSchedulerWhenHeartbeatSchedulerAlsoExists() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(AgentExecutorConfiguration.class, TaskJobAutoConfig.class);

        try {
            context.refresh();

            ITaskJobService taskJobService = context.getBean(ITaskJobService.class);
            TaskScheduler heartbeatScheduler = context.getBean(AgentExecutorNames.HEARTBEAT_SCHEDULER, TaskScheduler.class);
            TaskScheduler taskJobScheduler = context.getBean("xfgWrenchTaskScheduler", TaskScheduler.class);
            TaskScheduler injectedScheduler = (TaskScheduler) ReflectionTestUtils.getField(taskJobService, "taskScheduler");

            Assert.assertNotNull(taskJobService);
            Assert.assertNotNull(injectedScheduler);
            Assert.assertSame(taskJobScheduler, injectedScheduler);
            Assert.assertNotSame(heartbeatScheduler, injectedScheduler);
        } finally {
            context.close();
        }
    }
}
