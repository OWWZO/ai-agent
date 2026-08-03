package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.MDC;
import org.wwz.ai.config.AgentExecutorConfiguration;
import org.wwz.ai.config.executor.BoundedVirtualThreadExecutor;
import org.wwz.ai.domain.agent.ledger.model.AgentRunState;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.executor.AgentExecutorSupport;
import org.wwz.ai.domain.agent.runtime.llm.LlmPromptObservability;
import org.wwz.ai.types.agent.config.AgentExecutorProperties;
import org.wwz.ai.types.agent.visitor.VisitorRequestContext;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 有界虚拟线程执行器行为测试。
 */
public class BoundedVirtualThreadExecutorTest {

    @Test
    public void shouldKeepVirtualModeOffByDefaultAndEnablePerPool() {
        AgentExecutorProperties properties = new AgentExecutorProperties();
        Assert.assertFalse(properties.isVirtualThreadsEnabled());
        Assert.assertFalse(properties.getDispatch().isVirtualThreadsEnabled());

        properties.setVirtualThreadsEnabled(true);
        properties.getDispatch().setVirtualThreadsEnabled(true);
        Executor executor = new AgentExecutorConfiguration().agentDispatchExecutor(properties);
        try {
            Assert.assertTrue(executor instanceof BoundedVirtualThreadExecutor);
            Assert.assertEquals(32, ((BoundedVirtualThreadExecutor) executor).getMaxConcurrency());
        } finally {
            ((BoundedVirtualThreadExecutor) executor).shutdown();
        }
    }

    @Test
    public void shouldRejectAtCapacityAndReleasePermitAfterCompletion() throws Exception {
        BoundedVirtualThreadExecutor executor = new BoundedVirtualThreadExecutor("test", 1, "test-agent-");
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            executor.execute(() -> {
                started.countDown();
                await(release);
            }, "blocking-test", "request-1");
            Assert.assertTrue(started.await(2, TimeUnit.SECONDS));

            try {
                executor.execute(() -> { }, "overflow-test", "request-2");
                Assert.fail("capacity exhaustion must reject the task");
            } catch (RejectedExecutionException expected) {
                // 许可耗尽时必须快速拒绝，不能隐式排队。
            }

            release.countDown();
            waitUntil(() -> executor.getRunningTasks() == 0, 2000L);
            CountDownLatch admitted = new CountDownLatch(1);
            executor.execute(admitted::countDown, "after-release", "request-3");
            Assert.assertTrue(admitted.await(2, TimeUnit.SECONDS));
            Assert.assertEquals(1L, executor.snapshot().rejectedTasks());
        } finally {
            executor.shutdown();
        }
    }

    @Test
    public void shouldReleasePermitAfterFailureAndCancellation() throws Exception {
        BoundedVirtualThreadExecutor executor = new BoundedVirtualThreadExecutor("test", 1, "test-agent-");
        try {
            CompletableFuture<Void> failed = AgentExecutorSupport.supplyAsync(
                    executor, "failed-test", () -> {
                        throw new IllegalStateException("expected");
                    });
            try {
                failed.get(2, TimeUnit.SECONDS);
                Assert.fail("failed task must complete exceptionally");
            } catch (ExecutionException expected) {
                // 异常路径也必须释放许可。
            }
            AgentExecutorSupport.supplyAsync(executor, "after-failure", () -> null).get(2, TimeUnit.SECONDS);

            CountDownLatch started = new CountDownLatch(1);
            CompletableFuture<Void> cancelled = AgentExecutorSupport.supplyAsync(
                    executor, "cancelled-test", () -> {
                        started.countDown();
                        try {
                            Thread.sleep(10_000L);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return null;
                    });
            Assert.assertTrue(started.await(2, TimeUnit.SECONDS));
            Assert.assertTrue(cancelled.cancel(true));
            waitUntil(() -> executor.getRunningTasks() == 0, 2000L);
            AgentExecutorSupport.supplyAsync(executor, "after-cancel", () -> null).get(2, TimeUnit.SECONDS);
            Assert.assertTrue(executor.snapshot().cancelledTasks() >= 1);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    public void shouldInterruptUnderlyingTaskWhenTimeoutCompletesFuture() throws Exception {
        BoundedVirtualThreadExecutor executor = new BoundedVirtualThreadExecutor("timeout", 1, "timeout-agent-");
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        try {
            CompletableFuture<Void> timed = AgentExecutorSupport.withTimeout(
                    AgentExecutorSupport.supplyAsync(executor, "timeout-test", () -> {
                        started.countDown();
                        try {
                            Thread.sleep(10_000L);
                        } catch (InterruptedException e) {
                            interrupted.countDown();
                            Thread.currentThread().interrupt();
                        }
                        return null;
                    }),
                    50,
                    TimeUnit.MILLISECONDS);
            Assert.assertTrue(started.await(2, TimeUnit.SECONDS));
            try {
                timed.get(2, TimeUnit.SECONDS);
                Assert.fail("timed task must complete exceptionally");
            } catch (ExecutionException expected) {
                Assert.assertTrue(expected.getCause() instanceof TimeoutException);
            }
            Assert.assertTrue(interrupted.await(2, TimeUnit.SECONDS));
            waitUntil(() -> executor.getRunningTasks() == 0, 2000L);
            AgentExecutorSupport.supplyAsync(executor, "after-timeout", () -> null).get(2, TimeUnit.SECONDS);
            Assert.assertTrue(executor.snapshot().cancelledTasks() >= 1);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    public void shouldPropagateAndCleanRequestContextAcrossExecutorBoundary() throws Exception {
        BoundedVirtualThreadExecutor executor = new BoundedVirtualThreadExecutor("context", 2, "context-agent-");
        AgentRunState runState = AgentRunState.builder().build();
        AgentContext context = AgentContext.builder()
                .requestId("request-context")
                .agentRunState(runState)
                .build();
        context.markExecutionPosition("react-agent", 3);
        VisitorRequestContext.bind("visitor-context");
        MDC.put("requestId", "request-context");
        LlmPromptObservability.restore(LlmPromptObservability.ObservationBundle.builder()
                .systemFingerprint("fingerprint")
                .build());
        try {
            String threadName = AgentExecutorSupport.supplyAsync(
                    executor, "context-test", context, () -> {
                        Assert.assertEquals("visitor-context", VisitorRequestContext.currentVisitorId());
                        Assert.assertEquals("request-context", MDC.get("requestId"));
                        Assert.assertNotNull(LlmPromptObservability.current());
                        Assert.assertEquals("react-agent", runState.getCurrentAgentName());
                        Assert.assertEquals(Integer.valueOf(3), runState.getCurrentStepNo());
                        return Thread.currentThread().getName();
            }).get(2, TimeUnit.SECONDS);
            Assert.assertTrue(threadName.startsWith("context-agent-"));

            // 清空提交线程上下文，验证任务结束后不会把上一个任务的身份带入新任务。
            VisitorRequestContext.clear();
            MDC.clear();
            LlmPromptObservability.clear();
            AgentExecutorSupport.supplyAsync(executor, "cleanup-test", () -> {
                Assert.assertNull(VisitorRequestContext.currentVisitorId());
                Assert.assertNull(MDC.get("requestId"));
                Assert.assertNull(LlmPromptObservability.current());
                return null;
            }).get(2, TimeUnit.SECONDS);
        } finally {
            VisitorRequestContext.clear();
            MDC.clear();
            LlmPromptObservability.clear();
            executor.shutdown();
        }
    }

    @Test
    public void shouldRejectNewWorkAfterShutdown() {
        BoundedVirtualThreadExecutor executor = new BoundedVirtualThreadExecutor("shutdown", 1, "shutdown-agent-");
        executor.shutdown();
        try {
            executor.execute(() -> { }, "shutdown-test", "request-shutdown");
            Assert.fail("shutdown executor must reject new work");
        } catch (RejectedExecutionException expected) {
            Assert.assertTrue(executor.isShutdown());
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void waitUntil(Check check, long timeoutMillis) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (!check.matches() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10L);
        }
        Assert.assertTrue("condition was not met before timeout", check.matches());
    }

    @FunctionalInterface
    private interface Check {
        boolean matches();
    }
}
