package org.wwz.ai.test.domain.llm;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.wwz.ai.domain.agent.ledger.AgentExecutionRecorder;
import org.wwz.ai.domain.agent.ledger.model.AgentRunState;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.model.LlmInvocationFinishRecord;
import org.wwz.ai.domain.agent.ledger.model.LlmInvocationStartRecord;
import org.wwz.ai.domain.agent.cute.config.CuteConfig;
import org.wwz.ai.domain.agent.runtime.CuteLlmDependencies;
import org.wwz.ai.domain.agent.runtime.CuteRuntimeDependencies;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.cancel.RunCancellation;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.llm.LLM;
import org.wwz.ai.domain.agent.runtime.llm.LlmCompletionPort;
import org.wwz.ai.domain.agent.runtime.llm.LlmPromptObservability;
import org.wwz.ai.domain.agent.runtime.llm.LlmResponse;
import org.wwz.ai.domain.agent.runtime.llm.LlmStreamEvent;
import org.wwz.ai.domain.agent.runtime.llm.LlmUsage;
import org.wwz.ai.domain.agent.runtime.llm.StreamResponseHandler;

import java.util.List;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LLM 调用账本写入路径的契约回归。
 * <p>
 * 锁住拆分（把账本记录抽成独立类）时不能改变的行为：
 * 1. 只有「有活跃 run + 当前线程有 agentName」才建 invocation，否则 fail-open 跳过
 * 2. invocationSeq 每次调用递增，callKind / streaming / modelName 如实落账
 * 3. 观测快照先于账本创建，start 记录必须带上估算 token
 * 4. 终态按 success / stopped / failed 三分，finishedAt 与 errorMsg 随之不同
 */
public class LlmInvocationLedgerTest {

    private static final long RUN_ID = 42L;
    private static final long LLM_INVOCATION_ID = 9001L;
    private static final String MODEL = "test-model";

    @After
    public void clearObservability() {
        // logRequest 写的是 ThreadLocal，残留会把上一轮的指纹带进下一条用例。
        LlmPromptObservability.clear();
    }

    @Test
    public void startWritesInvocationWithSeqAndCallKind() {
        Harness harness = new Harness();

        harness.ask().join();

        LlmInvocationStartRecord start = harness.captureSingleStart();
        Assert.assertEquals(Long.valueOf(RUN_ID), start.getRunId());
        Assert.assertEquals("req-llm-ledger", start.getRequestId());
        Assert.assertEquals(Integer.valueOf(1), start.getInvocationSeq());
        Assert.assertEquals("react", start.getAgentName());
        Assert.assertEquals(Integer.valueOf(1), start.getStepNo());
        Assert.assertEquals(ExecutionLedgerConstants.CALL_KIND_ASK, start.getCallKind());
        Assert.assertEquals(Boolean.FALSE, start.getStreaming());
        Assert.assertEquals(MODEL, start.getModelName());
        Assert.assertNotNull("start 必须带墙钟起点", start.getStartedAt());
        // 观测快照先于账本创建：估算 token 来自 logRequest，不是 start 自己算的。
        Assert.assertNotNull(start.getEstSystemTokens());
        Assert.assertNotNull(start.getEstTotalTokens());
        Assert.assertNotNull(start.getMessageCount());
        Assert.assertEquals(Long.valueOf(LLM_INVOCATION_ID),
                harness.context.getAgentRunState().getCurrentLlmInvocationId());
    }

    @Test
    public void invocationSeqIncrementsPerCall() {
        Harness harness = new Harness();

        harness.ask().join();
        harness.ask().join();

        ArgumentCaptor<LlmInvocationStartRecord> captor =
                ArgumentCaptor.forClass(LlmInvocationStartRecord.class);
        verify(harness.recorder, times(2)).createLlmInvocation(captor.capture());
        Assert.assertEquals(Integer.valueOf(1), captor.getAllValues().get(0).getInvocationSeq());
        Assert.assertEquals(Integer.valueOf(2), captor.getAllValues().get(1).getInvocationSeq());
    }

    @Test
    public void startSkipsWhenAgentNameMissing() {
        // 没有 markExecutionPosition：账本无法归属到 agent/step，必须整条跳过而不是写半截。
        Harness harness = new Harness();
        harness.context.getAgentRunState().clearExecutionPosition();

        harness.ask().join();

        verify(harness.recorder, never()).createLlmInvocation(any());
        verify(harness.recorder, never()).finishLlmInvocation(any());
    }

    @Test
    public void startSkipsWithoutActiveLedgerRun() {
        Harness harness = new Harness();
        harness.context.setAgentRunState(AgentRunState.builder().build()); // 无 runId

        harness.ask().join();

        verify(harness.recorder, never()).createLlmInvocation(any());
        verify(harness.recorder, never()).finishLlmInvocation(any());
    }

    @Test
    public void finishCarriesSuccessUsageAndFinishReason() {
        Harness harness = new Harness();
        harness.response = LlmResponse.builder()
                .content("回答内容")
                .finishReason("stop")
                .usage(LlmUsage.builder().promptTokens(11).completionTokens(7).totalTokens(18).build())
                .build();

        harness.ask().join();

        LlmInvocationFinishRecord finish = harness.captureSingleFinish();
        Assert.assertEquals(Long.valueOf(LLM_INVOCATION_ID), finish.getLlmInvocationId());
        Assert.assertEquals(Integer.valueOf(ExecutionLedgerConstants.STATUS_SUCCESS), finish.getStatus());
        Assert.assertEquals("回答内容", finish.getResponseText());
        Assert.assertEquals(Integer.valueOf(11), finish.getPromptTokens());
        Assert.assertEquals(Integer.valueOf(7), finish.getCompletionTokens());
        Assert.assertEquals(Integer.valueOf(18), finish.getTotalTokens());
        Assert.assertEquals("stop", finish.getFinishReason());
        Assert.assertNotNull("成功终态必须有 finished_at", finish.getFinishedAt());
        Assert.assertNull(finish.getErrorMsg());
    }

    @Test
    public void finishMarksStoppedWhenRunCancelledDuringCall() {
        Harness harness = new Harness();
        harness.response = LlmResponse.builder().content("半截回答").finishReason("stop").build();
        harness.cancelDuringCall = true;

        try {
            harness.ask().join();
            Assert.fail("用户停止后必须抛取消异常");
        } catch (Exception expected) {
            // LlmCancelledException 由调用方按取消处理，不当失败重试。
        }

        LlmInvocationFinishRecord finish = harness.captureSingleFinish();
        Assert.assertEquals(Integer.valueOf(ExecutionLedgerConstants.STATUS_STOPPED), finish.getStatus());
        Assert.assertEquals("user_stop", finish.getErrorMsg());
        Assert.assertEquals("半截回答", finish.getResponseText());
    }

    @Test
    public void finishMarksFailedWhenProviderRejectsRequest() {
        Harness harness = new Harness();
        // 400 类错误被分类为不可重试，直接上抛，不需要等待退避。
        harness.failure = new IllegalArgumentException("invalid request");

        try {
            harness.ask().join();
            Assert.fail("provider 拒绝后必须把异常交给调用方");
        } catch (Exception expected) {
            // 非瞬态错误不重试。
        }

        LlmInvocationFinishRecord finish = harness.captureSingleFinish();
        Assert.assertEquals(Integer.valueOf(ExecutionLedgerConstants.STATUS_FAILED), finish.getStatus());
        Assert.assertNotNull(finish.getErrorMsg());
        Assert.assertTrue(finish.getErrorMsg().contains("invalid request"));
        Assert.assertNull(finish.getResponseText());
    }

    /** 最小可用的 LLM + 账本夹具。 */
    private static final class Harness {

        private final AgentExecutionRecorder recorder = mock(AgentExecutionRecorder.class);
        private final AgentContext context;
        private final LLM llm;

        private LlmResponse response;
        private RuntimeException failure;
        private boolean cancelDuringCall;

        private Harness() {
            when(recorder.createLlmInvocation(any())).thenReturn(LLM_INVOCATION_ID);
            response = LlmResponse.builder().content("回答内容").finishReason("stop").build();

            AgentRunState runState = AgentRunState.builder().runId(RUN_ID).build();
            runState.markExecutionPosition("react", 1);

            CuteConfig cuteConfig = new CuteConfig();
            cuteConfig.setLLMSettingsMap("""
                    {"%s":{"model":"%s","base_url":"https://llm.test","apikey":"test-key",
                    "max_tokens":1024,"temperature":0.0,"function_call_type":"function_call"}}
                    """.formatted(MODEL, MODEL));

            context = AgentContext.builder()
                    .requestId("req-llm-ledger")
                    .sessionId("session-llm-ledger")
                    .executionRecorder(recorder)
                    .agentRunState(runState)
                    .runCancellation(new RunCancellation())
                    .runtimeDependencies(runtimeDependencies(cuteConfig, this))
                    .build();
            llm = new LLM(MODEL, "erp", context.getRuntimeDependencies());
        }

        private java.util.concurrent.CompletableFuture<String> ask() {
            return llm.ask(context, List.of(Message.userMessage("你好", null)), List.of(), false, null);
        }

        private LlmInvocationStartRecord captureSingleStart() {
            ArgumentCaptor<LlmInvocationStartRecord> captor =
                    ArgumentCaptor.forClass(LlmInvocationStartRecord.class);
            verify(recorder, times(1)).createLlmInvocation(captor.capture());
            return captor.getValue();
        }

        private LlmInvocationFinishRecord captureSingleFinish() {
            ArgumentCaptor<LlmInvocationFinishRecord> captor =
                    ArgumentCaptor.forClass(LlmInvocationFinishRecord.class);
            verify(recorder, times(1)).finishLlmInvocation(captor.capture());
            return captor.getValue();
        }

        private static CuteRuntimeDependencies runtimeDependencies(CuteConfig cuteConfig, Harness harness) {
            return CuteRuntimeDependencies.builder()
                    .cuteConfig(cuteConfig)
                    .llmDependencies(CuteLlmDependencies.builder()
                            .completionPort(new StubCompletionPort(harness))
                            .streamResponseHandler(new StreamResponseHandler())
                            .build())
                    .llmExecutor(Runnable::run)
                    .build();
        }
    }

    /** 非流式端口：按夹具配置返回响应、抛错或在返回前触发取消。 */
    private static final class StubCompletionPort implements LlmCompletionPort {

        private final Harness harness;

        private StubCompletionPort(Harness harness) {
            this.harness = harness;
        }

        @Override
        public LlmResponse complete(org.wwz.ai.domain.agent.runtime.llm.LlmRequest request) {
            if (harness.cancelDuringCall) {
                harness.context.getRunCancellation().cancel(RunCancellation.REASON_USER_STOP);
            }
            if (harness.failure != null) {
                throw harness.failure;
            }
            return harness.response;
        }

        @Override
        public StreamCall stream(org.wwz.ai.domain.agent.runtime.llm.LlmRequest request) {
            return new StreamCall() {
                @Override
                public void subscribe(Consumer<LlmStreamEvent> onEvent,
                                      Consumer<Throwable> onError,
                                      Runnable onComplete) {
                    onError.accept(new IllegalStateException("stream not used in this test"));
                }

                @Override
                public void cancel() {
                }
            };
        }
    }
}
