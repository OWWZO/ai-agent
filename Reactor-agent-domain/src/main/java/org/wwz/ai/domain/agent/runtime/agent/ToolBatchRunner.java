package org.wwz.ai.domain.agent.runtime.agent;

import lombok.extern.slf4j.Slf4j;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.runtime.executor.AgentExecutorSupport;
import org.wwz.ai.domain.agent.runtime.tool.common.AgentDispatchTool;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.TaskToolNames;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * 工具批次的并发调度与收口：线程池选择、超时、取消、中断。
 * <p>
 * 只负责「等」，不决定每个工具成功与否——单工具结果由调用方在 future 的 handle 里写回。
 */
@Slf4j
final class ToolBatchRunner {

    private static final long CANCEL_POLL_INTERVAL_MILLIS = 100L;
    private static final long DEFAULT_TIMEOUT_SECONDS = 600L;

    private final BaseAgent agent;

    ToolBatchRunner(BaseAgent agent) {
        this.agent = agent;
    }

    Executor executorFor(ToolCall toolCall) {
        if (isAgentDispatchTool(toolCall)) {
            return resolveExecutor(ReactorRuntimeDependencies::requireTaskExecutor);
        }
        return resolveExecutor(ReactorRuntimeDependencies::requireToolExecutor);
    }

    boolean isAgentDispatchTool(ToolCall toolCall) {
        return toolCall != null
                && toolCall.getFunction() != null
                && AgentDispatchTool.NAME.equals(toolCall.getFunction().getName());
    }

    /**
     * 阻塞等待整批完成；期间按固定间隔检查取消信号与截止时间。
     * <p>
     * 超时或取消都会 cancel 未完成的 execution future，让线程池尽快回收。
     */
    void await(List<CompletableFuture<Void>> futures,
               List<CompletableFuture<?>> executionFutures,
               List<ToolCall> commands) {
        if (futures == null || futures.isEmpty()) {
            return;
        }
        CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        // TaskOutput 是长轮询，本身就该等到底，不套用批次超时。
        long timeoutSeconds = containsTaskOutput(commands) ? 0L : resolveToolBatchTimeoutSeconds();
        AgentContext context = agent.getContext();
        long deadlineNanos = timeoutSeconds > 0L
                ? System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
                : Long.MAX_VALUE;
        try {
            while (true) {
                if (context != null && context.isRunCancelled()) {
                    log.info("{} tool batch cancelled, cancelling unfinished tools", context.getRequestId());
                    cancelAll(executionFutures);
                    return;
                }

                long waitNanos = TimeUnit.MILLISECONDS.toNanos(CANCEL_POLL_INTERVAL_MILLIS);
                if (deadlineNanos != Long.MAX_VALUE) {
                    long remainingNanos = deadlineNanos - System.nanoTime();
                    if (remainingNanos <= 0L) {
                        log.error("{} tool batch timed out after {}s", requestId(), timeoutSeconds);
                        cancelAll(executionFutures);
                        return;
                    }
                    waitNanos = Math.min(waitNanos, remainingNanos);
                }

                try {
                    all.get(waitNanos, TimeUnit.NANOSECONDS);
                    return;
                } catch (TimeoutException ignored) {
                    // Recheck cancellation and the original batch deadline.
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("{} tool batch interrupted, cancelling unfinished tools", requestId());
            cancelAll(executionFutures);
        } catch (ExecutionException e) {
            Throwable root = unwrap(e.getCause());
            if (root instanceof TimeoutException) {
                log.error("{} tool batch timed out after {}s", requestId(), timeoutSeconds);
                cancelAll(executionFutures);
            } else {
                log.error("{} tool batch join failed", requestId(), root);
            }
        }
    }

    /** 剥离 CompletionException 包装，拿到真实异常。 */
    static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current == null ? error : current;
    }

    static boolean isCancellation(Throwable error) {
        return unwrap(error) instanceof CancellationException;
    }

    private void cancelAll(List<CompletableFuture<?>> executionFutures) {
        if (executionFutures == null) {
            return;
        }
        for (CompletableFuture<?> executionFuture : executionFutures) {
            if (executionFuture != null && !executionFuture.isDone()) {
                executionFuture.cancel(true);
            }
        }
    }

    private static boolean containsTaskOutput(List<ToolCall> commands) {
        if (commands == null) {
            return false;
        }
        for (ToolCall command : commands) {
            if (command == null || command.getFunction() == null) {
                continue;
            }
            if (TaskToolNames.TASK_OUTPUT.equals(command.getFunction().getName())) {
                return true;
            }
        }
        return false;
    }

    private long resolveToolBatchTimeoutSeconds() {
        AgentContext context = agent.getContext();
        if (context == null || context.getRuntimeDependencies() == null) {
            return DEFAULT_TIMEOUT_SECONDS;
        }
        return context.getRuntimeDependencies().resolveToolBatchTimeoutSeconds();
    }

    private Executor resolveExecutor(Function<ReactorRuntimeDependencies, Executor> picker) {
        AgentContext context = agent.getContext();
        if (context == null || context.getRuntimeDependencies() == null) {
            return Runnable::run;
        }
        return picker.apply(context.getRuntimeDependencies());
    }

    private String requestId() {
        AgentContext context = agent.getContext();
        return context == null ? "-" : context.getRequestId();
    }
}
