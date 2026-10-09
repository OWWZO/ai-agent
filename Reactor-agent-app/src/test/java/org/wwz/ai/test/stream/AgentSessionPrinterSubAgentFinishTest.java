package org.wwz.ai.test.stream;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.application.agent.stream.AgentStreamProjection;
import org.wwz.ai.application.agent.stream.AgentSessionPrinter;
import org.wwz.ai.application.agent.stream.AgentSessionStream;
import org.wwz.ai.application.agent.stream.AgentSessionStreamFrame;
import org.wwz.ai.domain.agent.runtime.command.AgentExecutionCommand;
import org.wwz.ai.domain.agent.runtime.stream.AgentStreamEvent;
import org.wwz.ai.domain.agent.runtime.stream.AgentStreamResult;
import org.wwz.ai.domain.agent.runtime.stream.ToolResultStreamPayload;
import org.wwz.ai.domain.agent.runtime.enums.AgentType;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentPrinter;
import org.wwz.ai.domain.agent.ledger.model.replay.ReplayTiming;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.LocalDateTime;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 子 Agent result 不得把主会话投影流标成 finished。
 */
public class AgentSessionPrinterSubAgentFinishTest {

    @Test
    public void nestedSubAgentResultMustNotFinishMainStream() throws Exception {
        CapturingStream stream = new CapturingStream();
        AgentExecutionCommand request = new AgentExecutionCommand();
        request.setRequestId("req-subagent-finish");
        AgentSessionPrinter printer = new AgentSessionPrinter(stream, request, 1);

        Map<String, Object> extra = new HashMap<>();
        extra.put(SubAgentPrinter.KEY_PARENT_TOOL_USE_ID, "parent-tool-1");
        extra.put(SubAgentPrinter.KEY_SUB_AGENT_ID, "sub-1");
        printer.sendWithResultMap("result", "子 Agent 报告", extra);

        Assert.assertEquals(1, stream.payloads.size());
        AgentStreamEvent response = (AgentStreamEvent) stream.payloads.get(0);
        Assert.assertEquals("result", response.getMessageType());
        Assert.assertFalse(Boolean.TRUE.equals(response.getFinish()));
        Assert.assertFalse(stream.completed.get());
    }

    @Test
    public void planApprovalYieldFinishesEnvelopeWithoutClosingStream() throws Exception {
        CapturingStream stream = new CapturingStream();
        AgentExecutionCommand request = new AgentExecutionCommand();
        request.setRequestId("req-plan-approval-finish");
        AgentSessionPrinter printer = new AgentSessionPrinter(stream, request, 1);

        Map<String, Object> payload = new HashMap<>();
        payload.put("status", "pending");
        payload.put("approvalId", "pa_1");
        printer.send("pa_1", "plan_approval", payload, false);

        Assert.assertEquals(1, stream.payloads.size());
        AgentStreamEvent response = (AgentStreamEvent) stream.payloads.get(0);
        Assert.assertEquals("plan_approval", response.getMessageType());
        Assert.assertTrue(Boolean.TRUE.equals(response.getFinish()));
        Assert.assertFalse(stream.completed.get());
    }

    @Test
    public void askUserQuestionYieldFinishesEnvelopeWithoutClosingStream() throws Exception {
        CapturingStream stream = new CapturingStream();
        AgentExecutionCommand request = new AgentExecutionCommand();
        request.setRequestId("req-ask-user-finish");
        AgentSessionPrinter printer = new AgentSessionPrinter(stream, request, 1);

        Map<String, Object> payload = new HashMap<>();
        payload.put("status", "pending");
        payload.put("questionId", "uq_1");
        printer.send("uq_1", "ask_user_question", payload, false);

        Assert.assertEquals(1, stream.payloads.size());
        AgentStreamEvent response = (AgentStreamEvent) stream.payloads.get(0);
        Assert.assertEquals("ask_user_question", response.getMessageType());
        Assert.assertTrue(Boolean.TRUE.equals(response.getFinish()));
        Assert.assertFalse(stream.completed.get());
    }

    @Test
    public void desktopControlYieldFinishesEnvelopeWithoutClosingStream() throws Exception {
        CapturingStream stream = new CapturingStream();
        AgentExecutionCommand request = new AgentExecutionCommand();
        request.setRequestId("req-desktop-finish");
        AgentSessionPrinter printer = new AgentSessionPrinter(stream, request, 1);

        Map<String, Object> payload = new HashMap<>();
        payload.put("status", "pending");
        payload.put("controlId", "dc_1");
        payload.put("streamUrl", "https://6080-box.e2b.app/vnc.html");
        printer.send("dc_1", "desktop_control", payload, false);

        Assert.assertEquals(1, stream.payloads.size());
        AgentStreamEvent response = (AgentStreamEvent) stream.payloads.get(0);
        Assert.assertEquals("desktop_control", response.getMessageType());
        Assert.assertTrue(Boolean.TRUE.equals(response.getFinish()));
        Assert.assertFalse(stream.completed.get());
    }

    @Test
    public void rootResultStillFinishesMainStream() throws Exception {
        CapturingStream stream = new CapturingStream();
        AgentExecutionCommand request = new AgentExecutionCommand();
        request.setRequestId("req-root-finish");
        AgentSessionPrinter printer = new AgentSessionPrinter(stream, request, 1);

        printer.send("result", "主 Agent 终答");

        Assert.assertEquals(1, stream.payloads.size());
        AgentStreamEvent response = (AgentStreamEvent) stream.payloads.get(0);
        Assert.assertEquals("result", response.getMessageType());
        Assert.assertTrue(Boolean.TRUE.equals(response.getFinish()));
    }

    @Test
    public void toolResultCarriesRuntimeTimingWithoutReplacingMessageTime() throws Exception {
        CapturingStream stream = new CapturingStream();
        AgentExecutionCommand request = new AgentExecutionCommand();
        request.setRequestId("req-tool-timing");
        AgentSessionPrinter printer = new AgentSessionPrinter(stream, request, 1);
        LocalDateTime startedAt = LocalDateTime.of(2026, 9, 26, 12, 1, 0);
        LocalDateTime finishedAt = startedAt.plusNanos(250_000_000L);

        printer.send("tool-call-timing", "tool_result",
                ToolResultStreamPayload.builder()
                        .toolName("read_tool")
                        .toolCallId("tool-call-timing")
                        .toolResult("ok")
                        .build(),
                Map.of("timing", ReplayTiming.builder()
                        .startedAt(startedAt)
                        .finishedAt(finishedAt)
                        .durationMs(250L)
                        .source(ReplayTiming.SOURCE_RUNTIME)
                        .build()),
                null,
                true);

        AgentStreamEvent response = (AgentStreamEvent) stream.payloads.get(0);
        Assert.assertNotNull(response.getMessageTime());
        Assert.assertEquals(ReplayTiming.SOURCE_RUNTIME, response.getTiming().getSource());
        Assert.assertEquals("tool-call-timing", response.getToolResult().getToolCallId());
    }

    @Test
    public void isNestedSubAgentEventDetectsTagsInMessageMap() {
        Map<String, Object> message = new HashMap<>();
        message.put(SubAgentPrinter.KEY_SUB_AGENT_ID, "sub-2");
        Assert.assertTrue(AgentSessionPrinter.isNestedSubAgentEvent(message, null));
        Assert.assertFalse(AgentSessionPrinter.isNestedSubAgentEvent("plain text", null));
    }

    @Test
    public void projectionStreamStaysOpenAfterNestedResult() throws Exception {
        CapturingStream downstream = new CapturingStream();
        AgentExecutionCommand request = new AgentExecutionCommand();
        request.setRequestId("req-projection-nested");
        request.setAgentType(5);

        AgentStreamProjection projection = new AgentStreamProjection(
                downstream,
                request,
                Map.of(AgentType.REACT, (req, agentResponse, agentRespList, eventResult) -> {
                    AgentStreamResult result = new AgentStreamResult();
                    result.setComplete(Boolean.TRUE.equals(agentResponse.getFinish()));
                    result.setStatus(Boolean.TRUE.equals(agentResponse.getFinish()) ? "success" : "running");
                    result.setRequestId(req.getRequestId());
                    result.setEventData(new HashMap<>());
                    return result;
                })
        );

        Map<String, Object> nestedMap = new HashMap<>();
        nestedMap.put(SubAgentPrinter.KEY_PARENT_TOOL_USE_ID, "parent-tool-1");
        nestedMap.put("agentType", 5);
        AgentStreamEvent nested = AgentStreamEvent.builder()
                .requestId(request.getRequestId())
                .messageId("m1")
                .messageType("result")
                .result("子 Agent 报告")
                .finish(false)
                .resultMap(nestedMap)
                .build();
        projection.send(nested);

        Assert.assertFalse(downstream.completed.get());
        Assert.assertEquals(1, downstream.payloads.size());

        Map<String, Object> rootMap = new HashMap<>();
        rootMap.put("agentType", 5);
        AgentStreamEvent root = AgentStreamEvent.builder()
                .requestId(request.getRequestId())
                .messageId("m2")
                .messageType("result")
                .result("主 Agent 终答")
                .finish(true)
                .resultMap(rootMap)
                .build();
        projection.send(root);

        // 根 result 的 finished 只收口业务态，不在投影层自动关流；
        // 由 GptQuery / HITL resume 在 finishRun、markAnswered 后显式 complete。
        Assert.assertFalse(downstream.completed.get());
        Assert.assertEquals(2, downstream.payloads.size());

        AgentStreamEvent settle = AgentStreamEvent.builder()
                .requestId(request.getRequestId())
                .messageId("m3")
                .messageType("stream_settle")
                .finish(true)
                .resultMap(rootMap)
                .build();
        projection.send(settle);
        Assert.assertTrue(downstream.completed.get());
    }

    @Test
    public void concurrentSendSerializesEventResultOrders() throws Exception {
        CapturingStream downstream = new CapturingStream();
        AgentExecutionCommand request = new AgentExecutionCommand();
        request.setRequestId("req-concurrent-proj");
        request.setAgentType(5);

        AgentStreamProjection projection = new AgentStreamProjection(
                downstream,
                request,
                Map.of(AgentType.REACT, (req, agentResponse, agentRespList, eventResult) -> {
                    int order = eventResult.getAndIncrOrder("tool_call");
                    AgentStreamResult result = new AgentStreamResult();
                    result.setComplete(false);
                    result.setStatus("running");
                    Map<String, Object> map = new HashMap<>();
                    map.put("order", order);
                    result.setEventData(map);
                    return result;
                })
        );

        int n = 40;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(n);
        for (int i = 0; i < n; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    start.await();
                    AgentStreamEvent resp = AgentStreamEvent.builder()
                            .requestId(request.getRequestId())
                            .messageId("m-" + idx)
                            .messageType("tool_call")
                            .finish(false)
                            .resultMap(new HashMap<>())
                            .build();
                    projection.send(resp);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        Assert.assertTrue(done.await(10, TimeUnit.SECONDS));
        pool.shutdownNow();

        Assert.assertEquals(n, downstream.payloads.size());
        Set<Integer> orders = new HashSet<>();
        for (Object payload : downstream.payloads) {
            AgentSessionStreamFrame result = (AgentSessionStreamFrame) payload;
            orders.add((Integer) result.getResultMap().get("order"));
        }
        Assert.assertEquals(n, orders.size());
        Assert.assertTrue(orders.contains(1));
        Assert.assertTrue(orders.contains(n));
    }

    private static final class CapturingStream implements AgentSessionStream {
        private final List<Object> payloads = new CopyOnWriteArrayList<>();
        private final AtomicBoolean completed = new AtomicBoolean(false);

        @Override
        public void send(Object payload) {
            payloads.add(payload);
        }

        @Override
        public void complete() {
            completed.set(true);
        }

        @Override
        public void completeWithError(Throwable throwable) {
            completed.set(true);
        }
    }
}
