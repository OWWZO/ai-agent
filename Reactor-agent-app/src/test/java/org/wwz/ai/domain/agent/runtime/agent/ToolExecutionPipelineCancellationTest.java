package org.wwz.ai.domain.agent.runtime.agent;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.runtime.cancel.RunCancellation;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class ToolExecutionPipelineCancellationTest {

    @Test
    public void shouldCancelPendingToolExecutionsWhenRunIsCancelled() throws Exception {
        RunCancellation cancellation = new RunCancellation();
        AgentContext context = AgentContext.builder()
                .requestId("request-stop-during-tool")
                .runCancellation(cancellation)
                .build();
        BaseAgent agent = new BaseAgent() {
            @Override
            public String step() {
                return "";
            }
        }.setContext(context);
        ToolExecutionPipeline pipeline = new ToolExecutionPipeline(agent);

        CompletableFuture<Object> execution = new CompletableFuture<>();
        CompletableFuture<Void> completion = execution.handle((value, error) -> null);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread waiter = new Thread(() -> {
            started.countDown();
            try {
                pipeline.awaitToolBatch(List.of(completion), List.of(execution), List.of());
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                finished.countDown();
            }
        });
        waiter.start();

        try {
            Assert.assertTrue(started.await(1, TimeUnit.SECONDS));
            long waitingDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            while (waiter.getState() != Thread.State.TIMED_WAITING
                    && System.nanoTime() < waitingDeadline) {
                Thread.yield();
            }
            Assert.assertEquals("工具批次未进入等待状态", Thread.State.TIMED_WAITING, waiter.getState());
            cancellation.cancel(RunCancellation.REASON_USER_STOP);

            Assert.assertTrue(
                    "取消后工具批次仍在等待工具完成",
                    finished.await(1, TimeUnit.SECONDS));
            Assert.assertTrue("停止信号没有取消工具 Future", execution.isCancelled());
            Assert.assertNull(failure.get());
        } finally {
            execution.complete(null);
            waiter.join(1000);
        }
    }

    @Test
    public void shouldNotStartToolsWhenCancelledAfterModelResponse() {
        RunCancellation cancellation = new RunCancellation();
        cancellation.cancel(RunCancellation.REASON_USER_STOP);
        AgentContext context = AgentContext.builder()
                .requestId("request-stop-before-tool")
                .runCancellation(cancellation)
                .build();
        AtomicBoolean executed = new AtomicBoolean(false);
        BaseAgent agent = new BaseAgent() {
            @Override
            public String step() {
                return "";
            }
        }.setContext(context);
        agent.getAvailableTools().addTool(new BaseTool() {
            @Override
            public String getName() {
                return "echo";
            }

            @Override
            public String getDescription() {
                return "";
            }

            @Override
            public Map<String, Object> toParams() {
                return Map.of();
            }

            @Override
            public Object execute(Object input) {
                executed.set(true);
                return "ran";
            }
        });
        ToolExecutionPipeline pipeline = new ToolExecutionPipeline(agent);

        Map<String, ToolExecutionOutcome> outcomes = pipeline.executeBatch(List.of(ToolCall.builder()
                .id("call-1")
                .function(ToolCall.Function.builder().name("echo").arguments("{}").build())
                .build()));

        Assert.assertFalse("模型已返回但用户已停止时不应启动工具", executed.get());
        Assert.assertEquals("USER_STOP", outcomes.get("call-1").getErrorMsg());
        Assert.assertEquals(ExecutionLedgerConstants.STATUS_STOPPED,
                ToolExecutionPipeline.ledgerStatus(outcomes.get("call-1")));
    }
}
