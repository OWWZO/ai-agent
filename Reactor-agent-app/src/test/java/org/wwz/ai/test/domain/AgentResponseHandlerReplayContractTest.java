package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.handler.BaseAgentResponseHandler;
import org.wwz.ai.domain.agent.reactor.model.multi.EventResult;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.reactor.model.response.AgentResponse;
import org.wwz.ai.domain.agent.reactor.model.response.GptProcessResult;
import org.wwz.ai.domain.agent.ledger.replay.ReplayProjector;
import org.wwz.ai.domain.agent.ledger.replay.projector.ToolInvocationProjectorRegistry;
import org.wwz.ai.domain.agent.ledger.replay.projector.impl.DefaultToolInvocationProjector;

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
        EventResult eventResult = new EventResult();
        eventResult.getResultMap().put("plannerRoundId", "planner-round-001");
        GptProcessResult result = handler.build(
                AgentRequest.builder().requestId("req-handler-001").build(),
                eventResult,
                AgentResponse.builder()
                        .requestId("req-handler-001")
                        .messageId("msg-plan-thought-1")
                        .messageType("plan_thought")
                        .messageTime("1714630000000")
                        .planThought("先规划执行步骤")
                        .isFinal(true)
                        .finish(false)
                        .resultMap(Map.of("agentType", 3, "plannerRoundId", "planner-round-001"))
                        .build()
        );

        Assert.assertEquals("plan_thought", eventData(result).get("messageType"));
        Assert.assertEquals("先规划执行步骤", frameResultMap(result).get("planThought"));
        Assert.assertEquals(Boolean.TRUE, frameResultMap(result).get("isFinal"));
        Assert.assertEquals("planner-round-001", frameResultMap(result).get("plannerRoundId"));
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

    @Test
    public void shouldKeepRealtimeSummaryFileListOnResultEvent() {
        List<Map<String, Object>> fileList = List.of(Map.of(
                "fileName", "summary.md",
                "downloadUrl", "https://file.example.com/summary.md"
        ));
        GptProcessResult result = handler.build(
                AgentRequest.builder().requestId("req-handler-003-file").build(),
                new EventResult(),
                AgentResponse.builder()
                        .requestId("req-handler-003-file")
                        .messageId("msg-result-file-1")
                        .messageType("result")
                        .messageTime("1714630002500")
                        .result("最终结论")
                        .isFinal(true)
                        .finish(true)
                        .resultMap(Map.of(
                                "agentType", 5,
                                "taskSummary", "最终结论",
                                "fileList", fileList
                        ))
                        .build()
        );

        Assert.assertEquals(fileList, frameResultMap(result).get("fileList"));
    }

    @Test
    public void shouldReuseSamePlannerRoundIdForPlanThoughtAndTaskWrappedPlan() {
        EventResult eventResult = new EventResult();
        eventResult.getResultMap().put("plannerRoundId", "planner-round-002");

        GptProcessResult thoughtFrame = handler.build(
                AgentRequest.builder().requestId("req-handler-004").build(),
                eventResult,
                AgentResponse.builder()
                        .requestId("req-handler-004")
                        .messageId("msg-plan-thought-2")
                        .messageType("plan_thought")
                        .messageTime("1714630003000")
                        .planThought("重排计划")
                        .isFinal(true)
                        .finish(false)
                        .resultMap(Map.of("agentType", 3, "plannerRoundId", "planner-round-002"))
                        .build()
        );
        GptProcessResult planFrame = handler.build(
                AgentRequest.builder().requestId("req-handler-004").build(),
                eventResult,
                AgentResponse.builder()
                        .requestId("req-handler-004")
                        .messageId("msg-plan-2")
                        .messageType("plan")
                        .messageTime("1714630003001")
                        .plan(AgentResponse.Plan.builder()
                                .title("第二轮计划")
                                .stages(List.of("阶段一"))
                                .steps(List.of("步骤一"))
                                .stepStatus(List.of("in_progress"))
                                .notes(List.of(""))
                                .build())
                        .isFinal(true)
                        .finish(false)
                        .resultMap(Map.of("agentType", 3, "plannerRoundId", "planner-round-002"))
                        .build()
        );

        Assert.assertEquals("planner-round-002", frameResultMap(thoughtFrame).get("plannerRoundId"));
        Assert.assertEquals("planner-round-002", frameResultMap(planFrame).get("plannerRoundId"));
    }

    @Test
    public void shouldEmitToolCallProgressAsTaskEvent() {
        GptProcessResult result = handler.build(
                AgentRequest.builder().requestId("req-handler-005").build(),
                new EventResult(),
                AgentResponse.builder()
                        .requestId("req-handler-005")
                        .messageId("tool-call-file-001")
                        .messageType("tool_call")
                        .messageTime("1714630004000")
                        .isFinal(false)
                        .finish(false)
                        .resultMap(Map.of(
                                "agentType", 5,
                                "toolCallId", "tool-call-file-001",
                                "toolName", "file_tool",
                                "status", "running",
                                "summary", "正在调用 file_tool"
                        ))
                        .build()
        );

        Assert.assertEquals("task", eventData(result).get("messageType"));
        Assert.assertEquals("tool_call", frameResultMap(result).get("messageType"));
        Assert.assertEquals("running", nestedResultMap(result).get("status"));
        Assert.assertEquals("file_tool", nestedResultMap(result).get("toolName"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> eventData(GptProcessResult frame) {
        return (Map<String, Object>) frame.getResultMap().get("eventData");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> frameResultMap(GptProcessResult frame) {
        return (Map<String, Object>) eventData(frame).get("resultMap");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nestedResultMap(GptProcessResult frame) {
        return (Map<String, Object>) frameResultMap(frame).get("resultMap");
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
