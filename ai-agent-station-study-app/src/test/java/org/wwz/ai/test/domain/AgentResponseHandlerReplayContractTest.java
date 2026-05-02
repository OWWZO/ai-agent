package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.reactor.handler.BaseAgentResponseHandler;
import org.wwz.ai.domain.agent.reactor.model.multi.EventResult;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.reactor.model.response.AgentResponse;
import org.wwz.ai.domain.agent.reactor.model.response.GptProcessResult;
import org.wwz.ai.domain.agent.reactor.service.replay.ReplayProjector;
import org.wwz.ai.domain.agent.reactor.service.replay.projector.ToolInvocationProjectorRegistry;
import org.wwz.ai.domain.agent.reactor.service.replay.projector.impl.DefaultToolInvocationProjector;

import java.util.List;
import java.util.Map;

/**
 * 锁定实时 response handler 输出的 eventData 契约，避免再次与历史回放分叉。
 */
public class AgentResponseHandlerReplayContractTest {

    private final TestableBaseAgentResponseHandler handler = new TestableBaseAgentResponseHandler(
            new ReplayProjector(new ToolInvocationProjectorRegistry(List.of(), new DefaultToolInvocationProjector()))
    );

    @Test
    public void shouldEmitPlanThoughtAsTopLevelPlanThoughtEvent() {
        GptProcessResult result = handler.build(
                AgentRequest.builder().requestId("req-handler-001").build(),
                new EventResult(),
                AgentResponse.builder()
                        .requestId("req-handler-001")
                        .messageId("msg-plan-thought-1")
                        .messageType("plan_thought")
                        .messageTime("1714630000000")
                        .planThought("先规划执行步骤")
                        .isFinal(true)
                        .finish(false)
                        .resultMap(Map.of("agentType", 3))
                        .build()
        );

        Assert.assertEquals("plan_thought", eventData(result).get("messageType"));
        Assert.assertEquals("先规划执行步骤", frameResultMap(result).get("planThought"));
        Assert.assertEquals(Boolean.TRUE, frameResultMap(result).get("isFinal"));
    }

    @Test
    public void shouldEmitToolThoughtAsTaskEventWithNestedLogicalMessageType() {
        GptProcessResult result = handler.build(
                AgentRequest.builder().requestId("req-handler-002").build(),
                new EventResult(),
                AgentResponse.builder()
                        .requestId("req-handler-002")
                        .messageId("msg-tool-thought-1")
                        .messageType("tool_thought")
                        .messageTime("1714630001000")
                        .toolThought("先读取本地文件")
                        .isFinal(true)
                        .finish(false)
                        .resultMap(Map.of("agentType", 5))
                        .build()
        );

        Assert.assertEquals("task", eventData(result).get("messageType"));
        Assert.assertEquals("tool_thought", frameResultMap(result).get("messageType"));
        Assert.assertEquals("先读取本地文件", frameResultMap(result).get("toolThought"));
    }

    @Test
    public void shouldKeepRealtimeAgentTypeInsteadOfHistoryMarker() {
        GptProcessResult result = handler.build(
                AgentRequest.builder().requestId("req-handler-003").build(),
                new EventResult(),
                AgentResponse.builder()
                        .requestId("req-handler-003")
                        .messageId("msg-result-1")
                        .messageType("result")
                        .messageTime("1714630002000")
                        .result("最终结论")
                        .isFinal(true)
                        .finish(true)
                        .resultMap(Map.of("agentType", 5, "taskSummary", "最终结论"))
                        .build()
        );

        Assert.assertEquals("5", String.valueOf(result.getResultMap().get("agentType")));
        Assert.assertEquals("task", eventData(result).get("messageType"));
        Assert.assertEquals("result", frameResultMap(result).get("messageType"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> eventData(GptProcessResult frame) {
        return (Map<String, Object>) frame.getResultMap().get("eventData");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> frameResultMap(GptProcessResult frame) {
        return (Map<String, Object>) eventData(frame).get("resultMap");
    }

    private static final class TestableBaseAgentResponseHandler extends BaseAgentResponseHandler {
        private TestableBaseAgentResponseHandler(ReplayProjector replayProjector) {
            super(replayProjector);
        }

        private GptProcessResult build(AgentRequest request, EventResult eventResult, AgentResponse response) {
            return buildCanonicalIncrResult(request, eventResult, response);
        }
    }
}
