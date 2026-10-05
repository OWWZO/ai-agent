package org.wwz.ai.test.domain;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.application.agent.authorization.ConversationSessionAuthorizationService;
import org.wwz.ai.domain.agent.ledger.ExecutionLedgerQueryService;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunView;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.model.ExecutionRunDetail;
import org.wwz.ai.domain.agent.ledger.model.LlmInvocationView;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationView;
import org.wwz.ai.domain.agent.ledger.replay.ConversationHistoryReplayService;
import org.wwz.ai.domain.agent.ledger.replay.HistoryReplayPrinter;
import org.wwz.ai.domain.agent.ledger.replay.ReplayProjector;
import org.wwz.ai.domain.agent.ledger.replay.projector.ToolInvocationProjectorRegistry;
import org.wwz.ai.domain.agent.ledger.replay.projector.impl.DefaultToolInvocationProjector;
import org.wwz.ai.domain.agent.reactor.model.response.GptProcessResult;
import org.wwz.ai.trigger.http.agent.AgentConversationHistoryController;
import org.wwz.ai.trigger.http.agent.vo.ConversationRunReplayRespVO;
import org.wwz.ai.types.agent.user.UserRequestContext;
import org.wwz.ai.types.enums.ResponseCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class ConversationHistoryRunningReplayControllerTest {

    @Before
    public void bindUser() {
        UserRequestContext.bind("user-test");
    }

    @After
    public void clearUser() {
        UserRequestContext.clear();
    }

    @Test
    public void shouldReplayRunningRunWithCompletedLedgerFacts() {
        String requestId = "req-replay-running-001";
        String sessionId = "session-replay-running-001";
        LocalDateTime startedAt = LocalDateTime.of(2026, 10, 2, 10, 0, 0);
        DialogueRunView run = DialogueRunView.builder()
                .id(7L)
                .requestId(requestId)
                .sessionId(sessionId)
                .status(ExecutionLedgerConstants.STATUS_RUNNING)
                .queryText("正在执行的问题")
                .entryAgent(ExecutionLedgerConstants.ENTRY_AGENT_REACT)
                .startedAt(startedAt)
                .build();
        LlmInvocationView llmInvocation = LlmInvocationView.builder()
                .id(11L)
                .runId(7L)
                .invocationSeq(1)
                .agentName("react")
                .callKind(ExecutionLedgerConstants.CALL_KIND_ASK)
                .responseText("准备读取相关资料")
                .status(ExecutionLedgerConstants.STATUS_SUCCESS)
                .startedAt(startedAt.plusSeconds(1))
                .finishedAt(startedAt.plusSeconds(2))
                .build();
        ToolInvocationView toolInvocation = ToolInvocationView.builder()
                .id(21L)
                .runId(7L)
                .llmInvocationId(11L)
                .requestId(requestId)
                .sessionId(sessionId)
                .toolCallId("call-running-001")
                .toolName("read_tool")
                .inputJson("{}")
                .llmObservation("资料读取完成")
                .status(ExecutionLedgerConstants.STATUS_SUCCESS)
                .startedAt(startedAt.plusSeconds(2))
                .finishedAt(startedAt.plusSeconds(3))
                .build();

        ExecutionLedgerQueryService queryService = Mockito.mock(ExecutionLedgerQueryService.class);
        Mockito.when(queryService.queryRunSummary(requestId)).thenReturn(run);
        Mockito.when(queryService.queryRunDetail(requestId)).thenReturn(ExecutionRunDetail.builder()
                .run(run)
                .llmInvocations(List.of(llmInvocation))
                .toolInvocations(List.of(toolInvocation))
                .artifacts(List.of())
                .build());
        ReplayProjector replayProjector = new ReplayProjector(new ToolInvocationProjectorRegistry(
                List.of(), new DefaultToolInvocationProjector()));
        ConversationHistoryReplayService replayService = new ConversationHistoryReplayService(
                queryService, replayProjector, new HistoryReplayPrinter(), null);

        AgentConversationHistoryController controller = new AgentConversationHistoryController();
        ReflectionTestUtils.setField(controller, "executionLedgerQueryService", queryService);
        ReflectionTestUtils.setField(controller, "conversationHistoryReplayService", replayService);
        ReflectionTestUtils.setField(controller, "conversationSessionAuthorizationService",
                Mockito.mock(ConversationSessionAuthorizationService.class));

        Response<ConversationRunReplayRespVO> response = controller.replay(requestId);

        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        Assert.assertNotNull(response.getData());
        Assert.assertEquals("RUNNING", response.getData().getStatus());
        Assert.assertNull(response.getData().getFinishedAt());
        List<GptProcessResult> replayFrames = response.getData().getReplayFrames();
        Assert.assertFalse(replayFrames.isEmpty());
        Assert.assertTrue(replayFrames.stream().anyMatch(
                frame -> "tool_result".equals(nestedResultMap(frame).get("messageType"))));
        Mockito.verify(queryService).queryRunDetail(requestId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nestedResultMap(GptProcessResult frame) {
        Map<String, Object> eventData = (Map<String, Object>) frame.getResultMap().get("eventData");
        return (Map<String, Object>) eventData.get("resultMap");
    }
}
