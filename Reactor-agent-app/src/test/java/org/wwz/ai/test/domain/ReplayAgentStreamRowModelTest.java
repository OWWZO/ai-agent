import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.ledger.model.LlmInvocationView;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationView;
import org.wwz.ai.domain.agent.ledger.model.replay.ReplayFactBundle;
import org.wwz.ai.domain.agent.runtime.stream.AgentStreamResult;
import org.wwz.ai.domain.agent.ledger.replay.ReplayProjector;
import org.wwz.ai.domain.agent.ledger.replay.projector.ToolInvocationProjectorRegistry;
import org.wwz.ai.domain.agent.ledger.replay.projector.impl.DefaultToolInvocationProjector;

/**
 * agent_stream 回放行模型回归：中间轮与终轮都必须投影正文。
 * 复现「刷新后最终回复消失 / 中间回复不夹在工具卡片之间」。
 */
public class ReplayAgentStreamRowModelTest {

    private final ReplayProjector replayProjector = new ReplayProjector(
            new ToolInvocationProjectorRegistry(
                    List.of(new DefaultToolInvocationProjector()),
                    new DefaultToolInvocationProjector()));

    @SuppressWarnings("unchecked")
    private Map<String, Object> inner(AgentStreamResult frame) {
        Map<String, Object> ed = (Map<String, Object>) frame.getEventData().get("eventData");
        return (Map<String, Object>) ed.get("resultMap");
    }

    @Test
    public void shouldProjectAgentStreamForMiddleAndFinalRounds() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 2, 12, 0, 0);
        // 第 1 轮调工具：中间轮发言，应夹在工具卡片之间
        LlmInvocationView middleRound = LlmInvocationView.builder()
                .id(1L).invocationSeq(1).agentName("react")
                .toolCallCount(1).responseText("我先查组件目录")
                .finishedAt(now.minusSeconds(30)).build();
        ToolInvocationView tool = ToolInvocationView.builder()
                .id(11L).llmInvocationId(1L).toolCallId("tool-call-1")
                .toolName("read_tool").llmObservation("目录内容")
                .finishedAt(now.minusSeconds(20)).build();
        // 第 2 轮无工具：终轮正文，不能再整段丢弃
        LlmInvocationView finalRound = LlmInvocationView.builder()
                .id(2L).invocationSeq(2).agentName("react")
                .toolCallCount(0).responseText("最终答案：界面已生成")
                .finishedAt(now.minusSeconds(5)).build();

        List<AgentStreamResult> frames = replayProjector.projectHistoryFrames(ReplayFactBundle.builder()
                .llmInvocations(List.of(middleRound, finalRound))
                .toolInvocations(List.of(tool))
                .build());

        Assert.assertEquals(3, frames.size());
        // 顺序：中间轮正文 -> 工具 -> 终轮正文（与实时 SSE 一致）
        Assert.assertEquals("agent_stream", inner(frames.get(0)).get("messageType"));
        Assert.assertEquals("我先查组件目录", inner(frames.get(0)).get("result"));
        Assert.assertEquals(Boolean.TRUE, inner(frames.get(0)).get("moreRounds"));
        Assert.assertEquals("tool_result", inner(frames.get(1)).get("messageType"));
        Assert.assertEquals("agent_stream", inner(frames.get(2)).get("messageType"));
        Assert.assertEquals("最终答案：界面已生成", inner(frames.get(2)).get("result"));
        Assert.assertEquals(Boolean.FALSE, inner(frames.get(2)).get("moreRounds"));
    }

    @Test
    public void shouldKeepExecutorAndPlanningLegacyMessageTypes() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 2, 12, 0, 0);
        List<AgentStreamResult> executorFrames = replayProjector.projectHistoryFrames(ReplayFactBundle.builder()
                .llmInvocations(List.of(LlmInvocationView.builder()
                        .id(3L).invocationSeq(1).agentName("executor")
                        .toolCallCount(0).responseText("任务总结")
                        .finishedAt(now).build()))
                .build());
        Assert.assertEquals(1, executorFrames.size());
        Assert.assertEquals("task_summary", inner(executorFrames.get(0)).get("messageType"));

        List<AgentStreamResult> planningFrames = replayProjector.projectHistoryFrames(ReplayFactBundle.builder()
                .llmInvocations(List.of(LlmInvocationView.builder()
                        .id(4L).invocationSeq(1).agentName("planning")
                        .responseText("先拆分执行计划").finishedAt(now).build()))
                .build());
        Assert.assertEquals(1, planningFrames.size());
        Assert.assertEquals("plan_thought", inner(planningFrames.get(0)).get("messageType"));
    }
}
