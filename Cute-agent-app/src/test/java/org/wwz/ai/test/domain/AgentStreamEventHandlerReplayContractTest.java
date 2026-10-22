package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.handler.BaseAgentStreamEventHandler;
import org.wwz.ai.domain.agent.runtime.stream.AgentStreamAccumulator;
import org.wwz.ai.domain.agent.runtime.command.AgentExecutionCommand;
import org.wwz.ai.domain.agent.runtime.stream.AgentStreamEvent;
import org.wwz.ai.domain.agent.runtime.stream.PlanStreamPayload;
import org.wwz.ai.domain.agent.runtime.stream.AgentStreamResult;
import org.wwz.ai.domain.agent.ledger.replay.ReplayProjector;
import org.wwz.ai.domain.agent.ledger.replay.projector.ToolInvocationProjectorRegistry;
import org.wwz.ai.domain.agent.ledger.replay.projector.impl.DefaultToolInvocationProjector;
import org.wwz.ai.domain.agent.ledger.model.replay.ReplayTiming;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 锁定实时 response handler 输出的 eventData 契约，避免再次与历史回放分叉。
 */
public class AgentStreamEventHandlerReplayContractTest {

    private final TestableBaseAgentStreamEventHandler handler = new TestableBaseAgentStreamEventHandler(
            new ReplayProjector(new ToolInvocationProjectorRegistry(List.of(), new DefaultToolInvocationProjector()))
    );

    @Test
    public void shouldEmitPlanThoughtAsTopLevelPlanThoughtEvent() {
        AgentStreamAccumulator eventResult = new AgentStreamAccumulator();
        eventResult.getResultMap().put("plannerRoundId", "planner-round-001");
        AgentStreamResult result = handler.build(
                AgentExecutionCommand.builder().requestId("req-handler-001").build(),
                eventResult,
                AgentStreamEvent.builder()
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
        AgentStreamResult result = handler.build(
                AgentExecutionCommand.builder().requestId("req-handler-002").build(),
                new AgentStreamAccumulator(),
                AgentStreamEvent.builder()
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
        AgentStreamResult result = handler.build(
                AgentExecutionCommand.builder().requestId("req-handler-003").build(),
                new AgentStreamAccumulator(),
                AgentStreamEvent.builder()
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

        Assert.assertEquals("5", String.valueOf(result.getEventData().get("agentType")));
        Assert.assertEquals("task", eventData(result).get("messageType"));
        Assert.assertEquals("result", frameResultMap(result).get("messageType"));
    }

    @Test
    public void shouldKeepRealtimeSummaryFileListOnResultEvent() {
        List<Map<String, Object>> fileList = List.of(Map.of(
                "fileName", "summary.md",
                "downloadUrl", "https://file.example.com/summary.md"
        ));
        List<Map<String, Object>> artifactRefs = List.of(Map.of(
                "resourceKey", "summary-call::summary.md",
                "displayName", "summary.md"
        ));
        AgentStreamResult result = handler.build(
                AgentExecutionCommand.builder().requestId("req-handler-003-file").build(),
                new AgentStreamAccumulator(),
                AgentStreamEvent.builder()
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
                                "fileList", fileList,
                                "artifactRefs", artifactRefs,
                                "artifactKeys", List.of("summary.md")
                        ))
                        .build()
        );

        Assert.assertEquals(fileList, frameResultMap(result).get("fileList"));
        Assert.assertEquals(artifactRefs, frameResultMap(result).get("artifactRefs"));
        Assert.assertEquals(List.of("summary.md"), frameResultMap(result).get("artifactKeys"));
    }

    @Test
    public void shouldReuseSamePlannerRoundIdForPlanThoughtAndTaskWrappedPlan() {
        AgentStreamAccumulator eventResult = new AgentStreamAccumulator();
        eventResult.getResultMap().put("plannerRoundId", "planner-round-002");

        AgentStreamResult thoughtFrame = handler.build(
                AgentExecutionCommand.builder().requestId("req-handler-004").build(),
                eventResult,
                AgentStreamEvent.builder()
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
        AgentStreamResult planFrame = handler.build(
                AgentExecutionCommand.builder().requestId("req-handler-004").build(),
                eventResult,
                AgentStreamEvent.builder()
                        .requestId("req-handler-004")
                        .messageId("msg-plan-2")
                        .messageType("plan")
                        .messageTime("1714630003001")
                        .plan(PlanStreamPayload.builder()
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
        AgentStreamResult result = handler.build(
                AgentExecutionCommand.builder().requestId("req-handler-005").build(),
                new AgentStreamAccumulator(),
                AgentStreamEvent.builder()
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

    @Test
    public void shouldProjectRuntimeTimingAtEventDataLevel() {
        LocalDateTime startedAt = LocalDateTime.of(2026, 9, 26, 12, 0, 0);
        LocalDateTime finishedAt = startedAt.plusNanos(125_000_000L);
        AgentStreamResult result = handler.build(
                AgentExecutionCommand.builder().requestId("req-handler-timing-001").build(),
                new AgentStreamAccumulator(),
                AgentStreamEvent.builder()
                        .requestId("req-handler-timing-001")
                        .messageId("tool-call-timing-001")
                        .messageType("tool_call")
                        .messageTime("1714630005000")
                        .resultMap(Map.of("toolCallId", "tool-call-timing-001"))
                        .timing(ReplayTiming.builder()
                                .startedAt(startedAt)
                                .finishedAt(finishedAt)
                                .durationMs(125L)
                                .source(ReplayTiming.SOURCE_RUNTIME)
                                .build())
                        .isFinal(true)
                        .finish(false)
                        .build()
        );

        Object timing = eventData(result).get("timing");
        Assert.assertTrue(timing instanceof ReplayTiming);
        Assert.assertEquals(ReplayTiming.SOURCE_RUNTIME, ((ReplayTiming) timing).getSource());
        Assert.assertEquals(Long.valueOf(125L), ((ReplayTiming) timing).getDurationMs());
        Assert.assertEquals("1714630005000", frameResultMap(result).get("messageTime"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> eventData(AgentStreamResult frame) {
        return (Map<String, Object>) frame.getEventData().get("eventData");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> frameResultMap(AgentStreamResult frame) {
        return (Map<String, Object>) eventData(frame).get("resultMap");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nestedResultMap(AgentStreamResult frame) {
        return (Map<String, Object>) frameResultMap(frame).get("resultMap");
    }

    private static final class TestableBaseAgentStreamEventHandler extends BaseAgentStreamEventHandler {
        private TestableBaseAgentStreamEventHandler(ReplayProjector replayProjector) {
            super(replayProjector);
        }

        private AgentStreamResult build(AgentExecutionCommand request, AgentStreamAccumulator eventResult, AgentStreamEvent response) {
            return buildCanonicalIncrResult(request, eventResult, response);
        }
    }
}
