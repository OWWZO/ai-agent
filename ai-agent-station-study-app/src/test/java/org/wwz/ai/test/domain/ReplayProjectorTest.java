package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.reactor.model.ledger.DialogueRunView;
import org.wwz.ai.domain.agent.reactor.model.ledger.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.reactor.model.ledger.LlmInvocationView;
import org.wwz.ai.domain.agent.reactor.model.ledger.ArtifactView;
import org.wwz.ai.domain.agent.reactor.model.ledger.ToolInvocationView;
import org.wwz.ai.domain.agent.reactor.model.response.GptProcessResult;
import org.wwz.ai.domain.agent.reactor.model.replay.ProjectedReplayEvent;
import org.wwz.ai.domain.agent.reactor.model.replay.ReplayFactBundle;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.FileToolOutput;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.ToolFileRef;
import org.wwz.ai.domain.agent.reactor.service.replay.ReplayProjector;
import org.wwz.ai.domain.agent.reactor.service.replay.projector.ToolInvocationProjectorRegistry;
import org.wwz.ai.domain.agent.reactor.service.replay.projector.impl.DefaultToolInvocationProjector;
import org.wwz.ai.domain.agent.reactor.service.replay.projector.impl.DeepSearchToolInvocationProjector;
import org.wwz.ai.domain.agent.reactor.service.replay.projector.impl.FileToolInvocationProjector;
import org.wwz.ai.domain.agent.reactor.service.replay.projector.impl.PlanningToolInvocationProjector;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * ReplayProjector 回归。
 * 验证共享回放入口会按 invocation 顺序委托 registry，而不是自己硬编码工具分支。
 */
public class ReplayProjectorTest {

    private final ReplayProjector replayProjector = new ReplayProjector(
            new ToolInvocationProjectorRegistry(
                    List.of(
                            new FileToolInvocationProjector(),
                            new PlanningToolInvocationProjector(),
                            new DeepSearchToolInvocationProjector(),
                            new DefaultToolInvocationProjector()
                    ),
                    new DefaultToolInvocationProjector()
            )
    );

    @Test
    public void shouldProjectBundleByToolNameAndInvocationOrder() {
        ToolInvocationView fileInvocation = ToolInvocationView.builder()
                .id(1L)
                .toolCallId("tool-call-file-001")
                .toolName("file_tool")
                .structuredOutput(FileToolOutput.builder()
                        .command("get")
                        .primaryFileName("report.md")
                        .fileRefs(List.of(ToolFileRef.builder().fileName("report.md").build()))
                        .build())
                .build();
        ToolInvocationView plainInvocation = ToolInvocationView.builder()
                .id(2L)
                .toolCallId("tool-call-plain-001")
                .toolName("read_tool")
                .llmObservation("hello")
                .build();
        ArtifactView artifact = ArtifactView.builder()
                .toolInvocationId(1L)
                .toolCallId("tool-call-file-001")
                .fileName("report.md")
                .downloadUrl("https://file.example.com/report.md")
                .previewUrl("https://file.example.com/preview/report.md")
                .storageKey("artifact-report")
                .build();

        List<ProjectedReplayEvent> events = replayProjector.projectHistory(ReplayFactBundle.builder()
                .toolInvocations(List.of(fileInvocation, plainInvocation))
                .artifacts(List.of(artifact))
                .build());

        Assert.assertEquals(2, events.size());
        Assert.assertEquals("file", outerMessageType(events.get(0)));
        Assert.assertEquals("tool_result", outerMessageType(events.get(1)));
        Assert.assertNotEquals(events.get(0).getTaskId(), events.get(1).getTaskId());
        Assert.assertEquals("读取文件", nestedResultMap(events.get(0)).get("command"));
        Assert.assertEquals("hello", toolResult(events.get(1)).get("toolResult"));
    }

    @Test
    public void shouldMapAgentNamesAndAppendStoppedSummaryFallback() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 2, 12, 0, 0);
        DialogueRunView run = DialogueRunView.builder()
                .requestId("req-replay-001")
                .status(ExecutionLedgerConstants.STATUS_STOPPED)
                .finalSummaryText("已按用户要求停止，并保留当前结论")
                .startedAt(now.minusMinutes(1))
                .finishedAt(now)
                .build();
        LlmInvocationView planningInvocation = LlmInvocationView.builder()
                .invocationSeq(1)
                .agentName("planning")
                .responseText("先拆分执行计划")
                .finishedAt(now.minusSeconds(30))
                .build();
        LlmInvocationView executorInvocation = LlmInvocationView.builder()
                .invocationSeq(2)
                .agentName("executor")
                .responseText("准备执行搜索工具")
                .finishedAt(now.minusSeconds(20))
                .build();

        List<GptProcessResult> frames = replayProjector.projectHistoryFrames(ReplayFactBundle.builder()
                .run(run)
                .llmInvocations(List.of(planningInvocation, executorInvocation))
                .build());

        Assert.assertEquals(3, frames.size());
        Assert.assertEquals("plan_thought", eventMessageType(frames.get(0)));
        Assert.assertEquals("task", eventMessageType(frames.get(1)));
        Assert.assertEquals("tool_thought", frameResultMap(frames.get(1)).get("messageType"));
        Assert.assertEquals("task", eventMessageType(frames.get(2)));
        Assert.assertEquals("result", frameResultMap(frames.get(2)).get("messageType"));
        Assert.assertEquals("先拆分执行计划", frameResultMap(frames.get(0)).get("planThought"));
        Assert.assertEquals("准备执行搜索工具", frameResultMap(frames.get(1)).get("toolThought"));
        Assert.assertEquals("已按用户要求停止，并保留当前结论", frameResultMap(frames.get(2)).get("result"));
    }

    @Test
    public void shouldProjectPlanningToolInvocationAsPlanAndTask() {
        ToolInvocationView planningInvocation = ToolInvocationView.builder()
                .id(10L)
                .toolCallId("tool-call-plan-001")
                .toolName("planning")
                .inputJson("{\"command\":\"create\",\"title\":\"调研计划\",\"steps\":[\"执行顺序1. 信息收集：搜集资料\",\"执行顺序2. 输出总结：整理结论\"]}")
                .status(ExecutionLedgerConstants.STATUS_SUCCESS)
                .finishedAt(LocalDateTime.of(2026, 5, 2, 15, 0, 0))
                .build();

        List<ProjectedReplayEvent> events = replayProjector.projectHistory(ReplayFactBundle.builder()
                .toolInvocations(List.of(planningInvocation))
                .build());

        Assert.assertEquals(2, events.size());
        Assert.assertEquals("plan", events.get(0).getMessageType());
        Assert.assertEquals("调研计划", plainResultMap(events.get(0)).get("title"));
        Assert.assertEquals("task", events.get(1).getMessageType());
        Assert.assertEquals("task", plainResultMap(events.get(1)).get("messageType"));
        Assert.assertEquals("信息收集：搜集资料", plainResultMap(events.get(1)).get("task"));
    }

    @Test
    public void shouldProjectExecutorNoToolLlmAsTaskSummary() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 2, 16, 0, 0);
        LlmInvocationView executorInvocation = LlmInvocationView.builder()
                .invocationSeq(3)
                .agentName("executor")
                .toolCallCount(0)
                .responseText("本轮任务已完成")
                .finishedAt(now)
                .build();

        List<GptProcessResult> frames = replayProjector.projectHistoryFrames(ReplayFactBundle.builder()
                .llmInvocations(List.of(executorInvocation))
                .build());

        Assert.assertEquals(1, frames.size());
        Assert.assertEquals("task", eventMessageType(frames.get(0)));
        Assert.assertEquals("task_summary", frameResultMap(frames.get(0)).get("messageType"));
        Assert.assertEquals("本轮任务已完成", frameResultMap(frames.get(0)).get("taskSummary"));
    }

    @Test
    public void shouldInterleaveThoughtAndToolByLlmInvocation() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 2, 17, 0, 0);
        LlmInvocationView firstExecutor = LlmInvocationView.builder()
                .id(101L)
                .invocationSeq(1)
                .agentName("executor")
                .toolCallCount(1)
                .responseText("先搜索第一批资料")
                .startedAt(now.minusSeconds(40))
                .finishedAt(now.minusSeconds(35))
                .build();
        LlmInvocationView secondExecutor = LlmInvocationView.builder()
                .id(102L)
                .invocationSeq(2)
                .agentName("executor")
                .toolCallCount(1)
                .responseText("再读取第二批资料")
                .startedAt(now.minusSeconds(25))
                .finishedAt(now.minusSeconds(20))
                .build();
        ToolInvocationView firstTool = ToolInvocationView.builder()
                .id(201L)
                .llmInvocationId(101L)
                .toolCallId("tool-call-1")
                .toolName("read_tool")
                .inputJson("{\"query\":\"第一批\"}")
                .llmObservation("第一批结果")
                .startedAt(now.minusSeconds(34))
                .finishedAt(now.minusSeconds(30))
                .build();
        ToolInvocationView secondTool = ToolInvocationView.builder()
                .id(202L)
                .llmInvocationId(102L)
                .toolCallId("tool-call-2")
                .toolName("read_tool")
                .inputJson("{\"query\":\"第二批\"}")
                .llmObservation("第二批结果")
                .startedAt(now.minusSeconds(19))
                .finishedAt(now.minusSeconds(15))
                .build();

        List<GptProcessResult> frames = replayProjector.projectHistoryFrames(ReplayFactBundle.builder()
                .llmInvocations(List.of(firstExecutor, secondExecutor))
                .toolInvocations(List.of(firstTool, secondTool))
                .build());

        Assert.assertEquals(4, frames.size());
        Assert.assertEquals("tool_thought", frameResultMap(frames.get(0)).get("messageType"));
        Assert.assertEquals("tool_result", frameResultMap(frames.get(1)).get("messageType"));
        Assert.assertEquals("tool_thought", frameResultMap(frames.get(2)).get("messageType"));
        Assert.assertEquals("tool_result", frameResultMap(frames.get(3)).get("messageType"));
        Assert.assertEquals(eventTaskId(frames.get(0)), eventTaskId(frames.get(1)));
        Assert.assertEquals(eventTaskId(frames.get(2)), eventTaskId(frames.get(3)));
        Assert.assertEquals(eventTaskId(frames.get(1)), eventTaskId(frames.get(2)));
    }

    @SuppressWarnings("unchecked")
    private String outerMessageType(ProjectedReplayEvent event) {
        return String.valueOf(((Map<String, Object>) event.getResultMap()).get("messageType"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nestedResultMap(ProjectedReplayEvent event) {
        return (Map<String, Object>) ((Map<String, Object>) event.getResultMap()).get("resultMap");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> plainResultMap(ProjectedReplayEvent event) {
        return (Map<String, Object>) event.getResultMap();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toolResult(ProjectedReplayEvent event) {
        return (Map<String, Object>) ((Map<String, Object>) event.getResultMap()).get("toolResult");
    }

    @SuppressWarnings("unchecked")
    private String eventMessageType(GptProcessResult frame) {
        return String.valueOf(((Map<String, Object>) frame.getResultMap().get("eventData")).get("messageType"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> frameResultMap(GptProcessResult frame) {
        return (Map<String, Object>) ((Map<String, Object>) frame.getResultMap().get("eventData")).get("resultMap");
    }

    @SuppressWarnings("unchecked")
    private String eventTaskId(GptProcessResult frame) {
        return String.valueOf(((Map<String, Object>) frame.getResultMap().get("eventData")).get("taskId"));
    }

}
