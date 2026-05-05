package org.wwz.ai.domain.agent.runtime.executor;

import org.wwz.ai.types.agent.exception.AgentExecutorBusyException;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

/**
 * Agent 主链路执行器公共提交工具。
 */
public final class AgentExecutorSupport {

    public static final String BUSY_MESSAGE = "系统繁忙，请稍后重试";

    private AgentExecutorSupport() {
    }

    /**
     * 用受控执行器包装 CompletableFuture，统一把拒绝语义收口为可观测异常。
     */
    public static <T> CompletableFuture<T> supplyAsync(Executor executor, String scene, Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier must not be null");
        try {
            return CompletableFuture.supplyAsync(supplier, requireExecutor(executor, scene));
        } catch (RejectedExecutionException e) {
            return failedFuture(rejected(scene, e));
        }
    }

    /**
     * 用受控执行器提交异步任务。
     */
    public static void execute(Executor executor, String scene, Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable must not be null");
        try {
            requireExecutor(executor, scene).execute(runnable);
        } catch (RejectedExecutionException e) {
            throw rejected(scene, e);
        }
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
}
