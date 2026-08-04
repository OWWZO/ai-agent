package org.wwz.ai.domain.agent.runtime.executor;

import org.slf4j.MDC;
import org.wwz.ai.domain.agent.ledger.model.AgentRunState;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.llm.LlmPromptObservability;
import org.wwz.ai.types.agent.exception.AgentExecutorBusyException;
import org.wwz.ai.types.agent.visitor.VisitorRequestContext;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Agent 主链路执行器公共提交工具。
 * <p>
 * 这里同时解决两个容易被忽略的问题：把拒绝转换成已完成的失败 Future，
 * 以及把请求、访客、MDC、LLM 观测和 Agent 运行位置带过线程池边界。
 */
public final class AgentExecutorSupport {

    public static final String BUSY_MESSAGE = "系统繁忙，请稍后重试";

    private AgentExecutorSupport() {
    }

    /**
     * 用受控执行器包装 CompletableFuture，统一把拒绝语义收口为可观测异常。
     */
    public static <T> CompletableFuture<T> supplyAsync(Executor executor, String scene, Supplier<T> supplier) {
        return supplyAsync(executor, scene, null, supplier);
    }

    /** 提交带 Agent 运行态的任务，跨平台/虚拟线程边界显式传递上下文。
     * 修复 DiscardPolicy / DiscardOldestPolicy 导致 Future 永久不 complete 的问题。
     */
    public static <T> CompletableFuture<T> supplyAsync(Executor executor,
                                                       String scene,
                                                       AgentContext context,
                                                       Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier must not be null");
        CancellableCompletableFuture<T> result = new CancellableCompletableFuture<>();
        TaskContextSnapshot snapshot = TaskContextSnapshot.capture(context);
        try {
            // 用 FutureTask 作为底层委托，保证 CompletableFuture 的 cancel/timeout 能真正中断执行任务。
            FutureTask<T> task = new FutureTask<>(() -> snapshot.call(supplier)) {
                @Override
                protected void done() {
                    if (isCancelled()) {
                        result.cancel(false);
                        return;
                    }
                    try {
                        result.complete(get());
                    } catch (CancellationException e) {
                        result.cancel(false);
                    } catch (Exception e) {
                        result.completeExceptionally(e.getCause() == null ? e : e.getCause());
                    }
                }
            };
            result.bind(task);
            submit(executor, scene, snapshot.requestId(), task);
            return result;
        } catch (RejectedExecutionException e) {
            return failedFuture(rejected(scene, e));
        }
    }

    /**
     * 为受控异步任务增加超时；超时完成对外 Future 后，同时中断底层 FutureTask。
     */
    public static <T> CompletableFuture<T> withTimeout(CompletableFuture<T> future,
                                                        long timeout,
                                                        TimeUnit unit) {
        Objects.requireNonNull(future, "future must not be null");
        Objects.requireNonNull(unit, "unit must not be null");
        if (future.isDone()) {
            return future;
        }
        CompletableFuture.delayedExecutor(timeout, unit).execute(() -> {
            TimeoutException timeoutException = new TimeoutException();
            // 先完成对外 Future，再取消底层任务；调用方不会因为执行器线程迟迟不退出而永久等待。
            if (future.completeExceptionally(timeoutException)
                    && future instanceof CancellableCompletableFuture<?> cancellable) {
                cancellable.cancelDelegate(true);
            }
        });
        return future;
    }

    /**
     * 用受控执行器提交异步任务。
     */
    public static void execute(Executor executor, String scene, Runnable runnable) {
        execute(executor, scene, (String) null, runnable);
    }

    public static void execute(Executor executor, String scene, String requestId, Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable must not be null");
        TaskContextSnapshot snapshot = TaskContextSnapshot.capture(null, requestId);
        try {
            submit(executor, scene, snapshot.requestId(), () -> snapshot.run(runnable));
        } catch (RejectedExecutionException e) {
            throw rejected(scene, e);
        }
    }

    /** 使用 AgentContext 提交 dispatch 之外的任务，保留 AgentRunState 的线程内视图。 */
    public static void execute(Executor executor, String scene, AgentContext context, Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable must not be null");
        TaskContextSnapshot snapshot = TaskContextSnapshot.capture(context);
        try {
            submit(executor, scene, snapshot.requestId(), () -> snapshot.run(runnable));
        } catch (RejectedExecutionException e) {
            throw rejected(scene, e);
        }
    }

    private static void submit(Executor executor, String scene, String requestId, Runnable task) {
        Executor required = requireExecutor(executor, scene);
        if (required instanceof AgentWorkExecutor workExecutor) {
            workExecutor.execute(task, scene, requestId);
            return;
        }
        required.execute(task);
    }

    private static Executor requireExecutor(Executor executor, String scene) {
        if (executor == null) {
            throw new IllegalStateException("缺少执行器: " + scene);
        }
        return executor;
    }

    private static AgentExecutorBusyException rejected(String scene, RejectedExecutionException cause) {
        return new AgentExecutorBusyException(scene + " 执行器繁忙，" + BUSY_MESSAGE, cause);
    }

    private static <T> CompletableFuture<T> failedFuture(Throwable throwable) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(throwable);
        return future;
    }

    /**
     * 把 CompletableFuture 的取消动作传递给底层 FutureTask，以便中断正在执行的线程。
     */
    private static final class CancellableCompletableFuture<T> extends CompletableFuture<T> {
        private volatile Future<?> delegate;

        private void bind(Future<?> delegate) {
            this.delegate = delegate;
            if (isCancelled()) {
                delegate.cancel(true);
            }
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean cancelled = super.cancel(mayInterruptIfRunning);
            if (cancelled) {
                cancelDelegate(mayInterruptIfRunning);
            }
            return cancelled;
        }

        private boolean cancelDelegate(boolean mayInterruptIfRunning) {
            Future<?> current = delegate;
            return current != null && current.cancel(mayInterruptIfRunning);
        }
    }

    private record TaskContextSnapshot(String requestId,
                                       String visitorId,
                                       Map<String, String> mdc,
                                       LlmPromptObservability.ObservationBundle observation,
                                       AgentRunState runState,
                                       String agentName,
                                       Integer stepNo,
                                       Long llmInvocationId) {

        private static TaskContextSnapshot capture(AgentContext context) {
            return capture(context, context == null ? null : context.getRequestId());
        }

        private static TaskContextSnapshot capture(AgentContext context, String requestId) {
            AgentRunState state = context == null ? null : context.getAgentRunState();
            String resolvedRequestId = requestId == null && context != null ? context.getRequestId() : requestId;
            Map<String, String> capturedMdc = MDC.getCopyOfContextMap();
            if (capturedMdc != null) {
                capturedMdc = Map.copyOf(capturedMdc);
            }
            return new TaskContextSnapshot(
                    resolvedRequestId,
                    VisitorRequestContext.currentVisitorId(),
                    capturedMdc,
                    LlmPromptObservability.current(),
                    state,
                    state == null ? null : state.getCurrentAgentName(),
                    state == null ? null : state.getCurrentStepNo(),
                    state == null ? null : state.getCurrentLlmInvocationId()
            );
        }

        private <T> T call(Supplier<T> supplier) {
            Scope scope = open();
            try {
                return supplier.get();
            } finally {
                scope.close();
            }
        }

        private void run(Runnable runnable) {
            Scope scope = open();
            try {
                runnable.run();
            } finally {
                scope.close();
            }
        }

        private Scope open() {
            // 线程池线程可能复用上一个请求的 ThreadLocal，进入任务前先保存并覆盖所有运行态。
            String previousVisitorId = VisitorRequestContext.currentVisitorId();
            Map<String, String> previousMdc = MDC.getCopyOfContextMap();
            LlmPromptObservability.ObservationBundle previousObservation = LlmPromptObservability.current();
            String previousAgentName = runState == null ? null : runState.getCurrentAgentName();
            Integer previousStepNo = runState == null ? null : runState.getCurrentStepNo();
            Long previousLlmInvocationId = runState == null ? null : runState.getCurrentLlmInvocationId();

            if (visitorId == null) {
                VisitorRequestContext.clear();
            } else {
                VisitorRequestContext.bind(visitorId);
            }
            if (mdc == null || mdc.isEmpty()) {
                MDC.clear();
            } else {
                MDC.setContextMap(mdc);
            }
            if (observation == null) {
                LlmPromptObservability.clear();
            } else {
                LlmPromptObservability.restore(observation);
            }
            if (runState != null) {
                if (agentName == null && stepNo == null) {
                    runState.clearExecutionPosition();
                } else {
                    runState.markExecutionPosition(agentName, stepNo);
                }
                if (llmInvocationId == null) {
                    runState.clearCurrentLlmInvocationId();
                } else {
                    runState.bindCurrentLlmInvocationId(llmInvocationId);
                }
            }
            return new Scope(previousVisitorId, previousMdc, previousObservation,
                    previousAgentName, previousStepNo, previousLlmInvocationId);
        }

        private final class Scope {
            private final String previousVisitorId;
            private final Map<String, String> previousMdc;
            private final LlmPromptObservability.ObservationBundle previousObservation;
            private final String previousAgentName;
            private final Integer previousStepNo;
            private final Long previousLlmInvocationId;

            private Scope(String previousVisitorId,
                          Map<String, String> previousMdc,
                          LlmPromptObservability.ObservationBundle previousObservation,
                          String previousAgentName,
                          Integer previousStepNo,
                          Long previousLlmInvocationId) {
                this.previousVisitorId = previousVisitorId;
                this.previousMdc = previousMdc;
                this.previousObservation = previousObservation;
                this.previousAgentName = previousAgentName;
                this.previousStepNo = previousStepNo;
                this.previousLlmInvocationId = previousLlmInvocationId;
            }

            private void close() {
                // 无论任务成功、失败还是取消，都恢复线程原有上下文，避免请求之间互相污染。
                if (previousVisitorId == null) {
                    VisitorRequestContext.clear();
                } else {
                    VisitorRequestContext.bind(previousVisitorId);
                }
                if (previousMdc == null || previousMdc.isEmpty()) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(previousMdc);
                }
                if (previousObservation == null) {
                    LlmPromptObservability.clear();
                } else {
                    LlmPromptObservability.restore(previousObservation);
                }
                if (runState != null) {
                    if (previousAgentName == null && previousStepNo == null) {
                        runState.clearExecutionPosition();
                    } else {
                        runState.markExecutionPosition(previousAgentName, previousStepNo);
                    }
                    if (previousLlmInvocationId == null) {
                        runState.clearCurrentLlmInvocationId();
                    } else {
                        runState.bindCurrentLlmInvocationId(previousLlmInvocationId);
                    }
                }
            }
        }
    }
}
