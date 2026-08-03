package org.wwz.ai.config.executor;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 注册 Agent 各命名执行器的容量、运行、拒绝、耗时和取消观测。
 */
public final class AgentExecutorMetrics {

    public AgentExecutorMetrics(MeterRegistry registry,
                                Executor dispatchExecutor,
                                Executor llmExecutor,
                                Executor taskExecutor,
                                Executor toolExecutor) {
        register(registry, "dispatch", dispatchExecutor);
        register(registry, "llm", llmExecutor);
        register(registry, "task", taskExecutor);
        register(registry, "tool", toolExecutor);
    }

    private void register(MeterRegistry registry, String name, Executor executor) {
        boolean virtual = executor instanceof BoundedVirtualThreadExecutor;
        Tags tags = Tags.of("executor", name, "mode", virtual ? "virtual" : "platform");
        Gauge.builder("agent.executor.capacity", executor, this::capacity)
                .tags(tags)
                .description("Agent 执行器配置的最大同时执行任务数")
                .register(registry);
        Gauge.builder("agent.executor.running", executor, this::running)
                .tags(tags)
                .description("Agent 执行器当前运行任务数")
                .register(registry);
        FunctionCounter.builder("agent.executor.rejections", executor, this::rejections)
                .tags(tags)
                .description("Agent 执行器累计拒绝任务数")
                .register(registry);
        FunctionCounter.builder("agent.executor.permit.wait", executor, this::permitWaitNanos)
                .tags(tags)
                .description("Agent 虚拟线程执行器累计许可准入耗时，平台执行器为零")
                .register(registry);
        FunctionCounter.builder("agent.executor.task.duration", executor, this::taskDurationNanos)
                .tags(tags)
                .description("Agent 执行器累计任务执行耗时")
                .register(registry);
        FunctionCounter.builder("agent.executor.cancellations", executor, this::cancellations)
                .tags(tags)
                .description("Agent 执行器累计取消任务数")
                .register(registry);
        if (executor instanceof ThreadPoolTaskExecutor taskExecutor) {
            ThreadPoolExecutor pool = taskExecutor.getThreadPoolExecutor();
            Gauge.builder("agent.executor.queue", pool, value -> value.getQueue().size())
                    .tags(tags)
                    .description("Agent 平台执行器队列长度")
                    .register(registry);
        }
    }

    private double capacity(Executor executor) {
        if (executor instanceof BoundedVirtualThreadExecutor virtual) {
            return virtual.snapshot().capacity();
        }
        return platform(executor) == null ? 0 : platform(executor).getMaximumPoolSize();
    }

    private double running(Executor executor) {
        if (executor instanceof BoundedVirtualThreadExecutor virtual) {
            return virtual.snapshot().runningTasks();
        }
        return platform(executor) == null ? 0 : platform(executor).getActiveCount();
    }

    private double rejections(Executor executor) {
        if (executor instanceof BoundedVirtualThreadExecutor virtual) {
            return virtual.snapshot().rejectedTasks();
        }
        if (platform(executor) != null
                && platform(executor).getRejectedExecutionHandler() instanceof CountingRejectedExecutionHandler handler) {
            return handler.getRejectedTasks();
        }
        return 0;
    }

    private double permitWaitNanos(Executor executor) {
        return executor instanceof BoundedVirtualThreadExecutor virtual
                ? virtual.snapshot().permitWaitNanos()
                : 0;
    }

    private double taskDurationNanos(Executor executor) {
        return executor instanceof BoundedVirtualThreadExecutor virtual
                ? virtual.snapshot().taskDurationNanos()
                : 0;
    }

    private double cancellations(Executor executor) {
        return executor instanceof BoundedVirtualThreadExecutor virtual
                ? virtual.snapshot().cancelledTasks()
                : 0;
    }

    private ThreadPoolExecutor platform(Executor executor) {
        return executor instanceof ThreadPoolTaskExecutor taskExecutor
                ? taskExecutor.getThreadPoolExecutor()
                : null;
    }
}
