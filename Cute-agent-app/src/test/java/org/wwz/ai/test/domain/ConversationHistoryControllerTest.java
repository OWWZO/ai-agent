package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.domain.agent.ledger.model.ArtifactRecordCommand;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunFinishRecord;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunStartRecord;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationBatchStartRecord;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationFinishRecord;
import org.wwz.ai.trigger.http.agent.vo.AgentStreamResponseVO;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.FileToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolFileRef;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolStructuredOutput;
import org.wwz.ai.trigger.http.agent.AgentConversationHistoryController;
import org.wwz.ai.trigger.http.agent.vo.ArtifactReferenceRespVO;
import org.wwz.ai.trigger.http.agent.vo.ConversationHistoryPageRespVO;
import org.wwz.ai.trigger.http.agent.vo.ConversationRunReplayRespVO;
import org.wwz.ai.trigger.http.agent.vo.ConversationSessionRespVO;
import org.wwz.ai.trigger.http.agent.vo.ConversationSessionPageRespVO;
import org.wwz.ai.application.agent.authorization.ConversationSessionAuthorizationService;
import org.wwz.ai.types.agent.user.UserRequestContext;
import org.wwz.ai.types.enums.ResponseCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 会话历史接口回归测试。
 */
public class ConversationHistoryControllerTest {

    @Before
    public void bindUser() {
        UserRequestContext.bind("user-test");
    }

    @After
    public void clearUser() {
        UserRequestContext.clear();
    }

    @Test
    public void shouldReturnSessionSummaryPageWithoutReplayFrames() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        seedRun(ctx, "req-history-001", "session-history-001", "file_tool",
                "先分析项目风险", LocalDateTime.of(2026, 5, 2, 10, 0, 0),
                ExecutionLedgerConstants.STATUS_SUCCESS, "summary:req-history-001", "report-1.md");
        seedRun(ctx, "req-history-002", "session-history-001", "read_tool",
                "继续补充方案", LocalDateTime.of(2026, 5, 2, 10, 5, 0),
                ExecutionLedgerConstants.STATUS_FAILED, "summary:req-history-002", null);

        AgentConversationHistoryController controller = new AgentConversationHistoryController();
        ReflectionTestUtils.setField(controller, "executionLedgerQueryService", ctx.queryService);
        ReflectionTestUtils.setField(controller, "conversationHistoryReplayService", ctx.replayService);
        ReflectionTestUtils.setField(controller, "conversationSessionAuthorizationService",
                Mockito.mock(ConversationSessionAuthorizationService.class));

        int artifactQueriesBefore = ctx.store.queryArtifactsByRunIdsCount;
        int toolQueriesBefore = ctx.store.queryToolByRunIdsCount;
        int llmQueriesBefore = ctx.store.queryLlmByRunIdsCount;
        int richOutputQueriesBefore = ctx.store.readToolOutputByInvocationIdsCount;
        Response<ConversationHistoryPageRespVO> response = controller.detail("session-history-001", 20, null);

        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        Assert.assertNotNull(response.getData());
        ConversationHistoryPageRespVO detail = response.getData();
        Assert.assertEquals("session-history-001", detail.getSessionId());
        Assert.assertEquals("FAILED", detail.getStatus());
        Assert.assertEquals(Integer.valueOf(2), detail.getRunCount());
        Assert.assertEquals(Integer.valueOf(1), detail.getFinishedRunCount());
        Assert.assertEquals(Integer.valueOf(1), detail.getFailedRunCount());
        Assert.assertEquals(2, detail.getRuns().size());
        Assert.assertEquals("先分析项目风险", detail.getRuns().get(0).getQueryPreview());
        Assert.assertEquals("summary:req-history-002", detail.getRuns().get(1).getFinalSummaryPreview());
        Assert.assertTrue(detail.getRuns().get(0).getHasReplay());
        Assert.assertFalse(detail.isHasMore());
        Assert.assertEquals(artifactQueriesBefore, ctx.store.queryArtifactsByRunIdsCount);
        Assert.assertEquals(toolQueriesBefore, ctx.store.queryToolByRunIdsCount);
        Assert.assertEquals(llmQueriesBefore, ctx.store.queryLlmByRunIdsCount);
        Assert.assertEquals(richOutputQueriesBefore, ctx.store.readToolOutputByInvocationIdsCount);
    }

    @Test
    public void shouldReturnFullFinalSummaryWithPreviewOnSessionHistoryPage() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        String fullSummary = "中秋最好的团圆是回家（约".repeat(25) + "全文末尾";
        seedRun(ctx, "req-full-summary-001", "session-full-summary-001", "read_tool",
                "读取完整终答", LocalDateTime.of(2026, 5, 2, 10, 0, 0),
                ExecutionLedgerConstants.STATUS_SUCCESS, fullSummary, null);

        AgentConversationHistoryController controller = new AgentConversationHistoryController();
        ReflectionTestUtils.setField(controller, "executionLedgerQueryService", ctx.queryService);
        ReflectionTestUtils.setField(controller, "conversationHistoryReplayService", ctx.replayService);
        ReflectionTestUtils.setField(controller, "conversationSessionAuthorizationService",
                Mockito.mock(ConversationSessionAuthorizationService.class));

        Response<ConversationHistoryPageRespVO> response = controller.detail(
                "session-full-summary-001", 20, null);

        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        Assert.assertNotNull(response.getData());
        String preview = response.getData().getRuns().get(0).getFinalSummaryPreview();
        Assert.assertTrue(preview.length() < fullSummary.length());
        Assert.assertEquals(fullSummary, response.getData().getRuns().get(0).getFinalSummaryText());
    }

    @Test
    public void shouldReturnVisibleInputAndOutputFilesAcrossSessionRuns() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        seedRun(ctx, "req-files-001", "session-files-001", "file_tool",
                "第一轮生成文件", LocalDateTime.of(2026, 5, 2, 10, 0, 0),
                ExecutionLedgerConstants.STATUS_SUCCESS, "summary:req-files-001", "report-1.md");
        seedRun(ctx, "req-files-002", "session-files-001", "file_tool",
                "第二轮生成文件", LocalDateTime.of(2026, 5, 2, 10, 5, 0),
                ExecutionLedgerConstants.STATUS_SUCCESS, "summary:req-files-002", "report-2.md");

        Long firstRunId = ctx.store.runs.values().stream()
                .filter(run -> "session-files-001".equals(run.getSessionId()))
                .findFirst()
                .orElseThrow()
                .getId();
        ctx.recorder.recordArtifacts(List.of(
                ArtifactRecordCommand.builder()
                        .runId(firstRunId)
                        .requestId("req-files-001")
                        .artifactRole(ExecutionLedgerConstants.ARTIFACT_ROLE_INPUT)
                        .visibility(ExecutionLedgerConstants.VISIBILITY_VISIBLE)
                        .sourceType(ExecutionLedgerConstants.SOURCE_TYPE_USER_UPLOAD)
                        .fileName("input.csv")
                        .storageKey("workspace/input.csv")
                        .downloadUrl("https://file.example.com/download/input.csv")
                        .previewUrl("https://file.example.com/preview/input.csv")
                        .metadataJson("{\"relativePath\":\"uploads/input.csv\"}")
                        .build(),
                ArtifactRecordCommand.builder()
                        .runId(firstRunId)
                        .requestId("req-files-001")
                        .artifactRole(ExecutionLedgerConstants.ARTIFACT_ROLE_OUTPUT)
                        .visibility(ExecutionLedgerConstants.VISIBILITY_INTERNAL)
                        .sourceType(ExecutionLedgerConstants.SOURCE_TYPE_TOOL_OUTPUT)
                        .fileName("internal.md")
                        .storageKey("workspace/internal.md")
                        .build(),
                ArtifactRecordCommand.builder()
                        .runId(firstRunId)
                        .requestId("req-files-001")
                        .artifactRole(ExecutionLedgerConstants.ARTIFACT_ROLE_OUTPUT)
                        .visibility(ExecutionLedgerConstants.VISIBILITY_VISIBLE)
                        .sourceType(ExecutionLedgerConstants.SOURCE_TYPE_TOOL_OUTPUT)
                        .fileName("deleted.md")
                        .storageKey("workspace/deleted.md")
                        .build()
        ));
        ctx.store.artifacts.values().stream()
                .filter(artifact -> "report-1.md".equals(artifact.getFileName()))
                .findFirst()
                .orElseThrow()
                .setMetadataJson("{\"relativePath\":\"reports/report-1.md\"}");
        ctx.store.artifacts.values().stream()
                .filter(artifact -> "deleted.md".equals(artifact.getFileName()))
                .findFirst()
                .orElseThrow()
                .setDeleted(1);

        AgentConversationHistoryController controller = new AgentConversationHistoryController();
        ReflectionTestUtils.setField(controller, "executionLedgerQueryService", ctx.queryService);
        ReflectionTestUtils.setField(controller, "conversationHistoryReplayService", ctx.replayService);
        ReflectionTestUtils.setField(controller, "conversationSessionAuthorizationService",
                Mockito.mock(ConversationSessionAuthorizationService.class));

        Response<List<ArtifactReferenceRespVO>> response = controller.files("session-files-001");

        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        Assert.assertNotNull(response.getData());
        Assert.assertEquals(3, response.getData().size());
        ArtifactReferenceRespVO report = response.getData().stream()
                .filter(file -> "report-1.md".equals(file.getDisplayName()))
                .findFirst()
                .orElseThrow();
        Assert.assertEquals("reports/report-1.md", report.getRelativePath());
        Assert.assertEquals("oss://report-1.md", report.getResourceKey());
        Assert.assertEquals("https://file.example.com/preview/report-1.md", report.getPreviewUrl());
        Assert.assertEquals("https://file.example.com/download/report-1.md", report.getDownloadUrl());

        ArtifactReferenceRespVO input = response.getData().stream()
                .filter(file -> "input.csv".equals(file.getDisplayName()))
                .findFirst()
                .orElseThrow();
        Assert.assertEquals("uploads/input.csv", input.getRelativePath());
        Assert.assertEquals("workspace/input.csv", input.getResourceKey());
        Assert.assertEquals(1, ctx.store.queryArtifactsBySessionIdCount);
        Assert.assertEquals(0, ctx.store.queryLlmByRunIdsCount);
        Assert.assertEquals(0, ctx.store.queryToolByRunIdsCount);
        Assert.assertEquals(0, ctx.store.readToolOutputByInvocationIdsCount);
    }

    @Test
    public void shouldReplayOneRunOnlyAfterSessionOwnershipLookup() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        seedRun(ctx, "req-replay-001", "session-replay-001", "file_tool",
                "单 run 回放", LocalDateTime.of(2026, 5, 2, 10, 0, 0),
                ExecutionLedgerConstants.STATUS_SUCCESS, "summary:req-replay-001", "report-replay.md");

        AgentConversationHistoryController controller = new AgentConversationHistoryController();
        ReflectionTestUtils.setField(controller, "executionLedgerQueryService", ctx.queryService);
        ReflectionTestUtils.setField(controller, "conversationHistoryReplayService", ctx.replayService);
        ReflectionTestUtils.setField(controller, "conversationSessionAuthorizationService",
                Mockito.mock(ConversationSessionAuthorizationService.class));

        Response<ConversationRunReplayRespVO> response = controller.replay("req-replay-001");

        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        Assert.assertNotNull(response.getData());
        List<AgentStreamResponseVO> secondRunFrames = response.getData().getReplayFrames();
        Assert.assertFalse(secondRunFrames.isEmpty());
        Map<String, Object> finalResultMap = nestedResultMap(secondRunFrames.get(secondRunFrames.size() - 1));
        Assert.assertEquals("result", finalResultMap.get("messageType"));
        Assert.assertEquals("summary:req-replay-001", finalResultMap.get("result"));
    }

    @Test
    public void shouldRejectRunReplayBeforeLoadingLedgerFactsWhenOwnershipFails() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        seedRun(ctx, "req-owned-001", "session-owned-001", "file_tool",
                "private run", LocalDateTime.of(2026, 5, 2, 10, 0, 0),
                ExecutionLedgerConstants.STATUS_SUCCESS, "summary:req-owned-001", "private.md");

        ConversationSessionAuthorizationService ownership =
                Mockito.mock(ConversationSessionAuthorizationService.class);
        Mockito.doThrow(new RuntimeException("denied"))
                .when(ownership)
                .ensureExistingSessionAccessible("user-test", "session-owned-001");
        AgentConversationHistoryController controller = new AgentConversationHistoryController();
        ReflectionTestUtils.setField(controller, "executionLedgerQueryService", ctx.queryService);
        ReflectionTestUtils.setField(controller, "conversationHistoryReplayService", ctx.replayService);
        ReflectionTestUtils.setField(controller, "conversationSessionAuthorizationService", ownership);

        int llmQueriesBefore = ctx.store.queryLlmByRunIdCount;
        int toolQueriesBefore = ctx.store.queryToolByRunIdCount;
        int artifactQueriesBefore = ctx.store.queryArtifactsByRunIdCount;
        Response<ConversationRunReplayRespVO> response = controller.replay("req-owned-001");

        Assert.assertEquals(ResponseCode.UN_ERROR.getCode(), response.getCode());
        Assert.assertEquals(llmQueriesBefore, ctx.store.queryLlmByRunIdCount);
        Assert.assertEquals(toolQueriesBefore, ctx.store.queryToolByRunIdCount);
        Assert.assertEquals(artifactQueriesBefore, ctx.store.queryArtifactsByRunIdCount);
    }

    @Test
    public void shouldRejectSessionFilesBeforeLoadingArtifactsWhenOwnershipFails() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        seedRun(ctx, "req-files-owned-001", "session-files-owned-001", "file_tool",
                "private files", LocalDateTime.of(2026, 5, 2, 10, 0, 0),
                ExecutionLedgerConstants.STATUS_SUCCESS, "summary:req-files-owned-001", "private.md");

        ConversationSessionAuthorizationService ownership =
                Mockito.mock(ConversationSessionAuthorizationService.class);
        Mockito.doThrow(new RuntimeException("denied"))
                .when(ownership)
                .ensureExistingSessionAccessible("user-test", "session-files-owned-001");
        AgentConversationHistoryController controller = new AgentConversationHistoryController();
        ReflectionTestUtils.setField(controller, "executionLedgerQueryService", ctx.queryService);
        ReflectionTestUtils.setField(controller, "conversationHistoryReplayService", ctx.replayService);
        ReflectionTestUtils.setField(controller, "conversationSessionAuthorizationService", ownership);

        int artifactQueriesBefore = ctx.store.queryArtifactsBySessionIdCount;
        Response<List<ArtifactReferenceRespVO>> response = controller.files("session-files-owned-001");

        Assert.assertEquals(ResponseCode.UN_ERROR.getCode(), response.getCode());
        Assert.assertEquals(artifactQueriesBefore, ctx.store.queryArtifactsBySessionIdCount);
    }

    @Test
    public void shouldPageHistoryWithKeysetCursorAndRejectCrossSessionCursor() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        seedRun(ctx, "req-cursor-001", "session-cursor-001", "read_tool",
                "cursor first", LocalDateTime.of(2026, 5, 2, 10, 0, 0),
                ExecutionLedgerConstants.STATUS_SUCCESS, "summary:req-cursor-001", null);
        seedRun(ctx, "req-cursor-002", "session-cursor-001", "read_tool",
                "cursor second", LocalDateTime.of(2026, 5, 2, 10, 1, 0),
                ExecutionLedgerConstants.STATUS_WAITING_INPUT, "summary:req-cursor-002", null);
        seedRun(ctx, "req-cursor-other", "session-cursor-other", "read_tool",
                "other session", LocalDateTime.of(2026, 5, 2, 10, 2, 0),
                ExecutionLedgerConstants.STATUS_STOPPED, "summary:req-cursor-other", null);

        AgentConversationHistoryController controller = new AgentConversationHistoryController();
        ReflectionTestUtils.setField(controller, "executionLedgerQueryService", ctx.queryService);
        ReflectionTestUtils.setField(controller, "conversationHistoryReplayService", ctx.replayService);
        ReflectionTestUtils.setField(controller, "conversationSessionAuthorizationService",
                Mockito.mock(ConversationSessionAuthorizationService.class));

        Response<ConversationHistoryPageRespVO> first = controller.detail("session-cursor-001", 1, null);
        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), first.getCode());
        Assert.assertEquals(1, first.getData().getRuns().size());
        Assert.assertTrue(first.getData().isHasMore());
        Assert.assertNotNull(first.getData().getNextCursor());

        Response<ConversationHistoryPageRespVO> second = controller.detail(
                "session-cursor-001", 1, first.getData().getNextCursor());
        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), second.getCode());
        Assert.assertEquals("req-cursor-002", second.getData().getRuns().get(0).getRequestId());
        Assert.assertFalse(second.getData().isHasMore());

        Response<ConversationHistoryPageRespVO> crossSession = controller.detail(
                "session-cursor-other", 1, first.getData().getNextCursor());
        Assert.assertEquals(ResponseCode.UN_ERROR.getCode(), crossSession.getCode());
    }

    @Test
    public void shouldReturnRecentSessionsOrderedByLastActiveAt() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        seedRun(ctx, "req-list-001", "session-list-001", "file_tool",
                "第一个会话", LocalDateTime.of(2026, 5, 2, 9, 0, 0),
                ExecutionLedgerConstants.STATUS_SUCCESS, "summary:req-list-001", "report-list-001.md");
        seedRun(ctx, "req-list-002", "session-list-002", "read_tool",
                "第二个会话", LocalDateTime.of(2026, 5, 2, 9, 30, 0),
                ExecutionLedgerConstants.STATUS_FAILED, "summary:req-list-002", null);

        AgentConversationHistoryController controller = new AgentConversationHistoryController();
        ReflectionTestUtils.setField(controller, "executionLedgerQueryService", ctx.queryService);
        ReflectionTestUtils.setField(controller, "conversationHistoryReplayService", ctx.replayService);
        ReflectionTestUtils.setField(controller, "conversationSessionAuthorizationService",
                Mockito.mock(ConversationSessionAuthorizationService.class));

        Response<ConversationSessionPageRespVO> response = controller.list(1, null);

        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        Assert.assertNotNull(response.getData());
        Assert.assertEquals(1, response.getData().getSessions().size());
        Assert.assertTrue(response.getData().isHasMore());
        Assert.assertNotNull(response.getData().getNextCursor());
        Assert.assertEquals("session-list-002", response.getData().getSessions().get(0).getSessionId());
        Assert.assertEquals("FAILED", response.getData().getSessions().get(0).getStatus());
        Assert.assertEquals("第二个会话", response.getData().getSessions().get(0).getLatestQueryText());

        Response<ConversationSessionPageRespVO> secondPage = controller.list(
                1,
                response.getData().getNextCursor()
        );
        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), secondPage.getCode());
        Assert.assertFalse(secondPage.getData().isHasMore());
        Assert.assertEquals("session-list-001", secondPage.getData().getSessions().get(0).getSessionId());
        Assert.assertEquals("SUCCESS", secondPage.getData().getSessions().get(0).getStatus());
    }

    @Test
    public void shouldRestoreReactSessionModeFromHistoryDetail() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        String fileName = "react-history-report.html";
        seedRun(
                ctx,
                "req-react-structured-001",
                "session-react-structured-001",
                ExecutionLedgerConstants.ENTRY_AGENT_REACT,
                "report_tool",
                "帮我输出网页报告",
                LocalDateTime.of(2026, 5, 2, 11, 0, 0),
                ExecutionLedgerConstants.STATUS_SUCCESS,
                "summary:req-react-structured-001",
                null,
                fileName
        );

        AgentConversationHistoryController controller = new AgentConversationHistoryController();
        ReflectionTestUtils.setField(controller, "executionLedgerQueryService", ctx.queryService);
        ReflectionTestUtils.setField(controller, "conversationHistoryReplayService", ctx.replayService);
        ReflectionTestUtils.setField(controller, "conversationSessionAuthorizationService",
                Mockito.mock(ConversationSessionAuthorizationService.class));

        Response<ConversationHistoryPageRespVO> response = controller.detail("session-react-structured-001", 20, null);

        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        Assert.assertNotNull(response.getData());
        Assert.assertEquals(Boolean.FALSE, response.getData().getDeepThink());
    }

    @Test
    public void shouldRestorePlanSolveHistoryMode() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        String fileName = "plan-solve-history-report.html";
        seedRun(
                ctx,
                "req-plan-solve-001",
                "session-plan-solve-001",
                ExecutionLedgerConstants.ENTRY_AGENT_PLAN_SOLVE,
                "report_tool",
                "帮我做深度研究并输出网页报告",
                LocalDateTime.of(2026, 5, 2, 11, 30, 0),
                ExecutionLedgerConstants.STATUS_SUCCESS,
                "summary:req-plan-solve-001",
                null,
                fileName
        );

        AgentConversationHistoryController controller = new AgentConversationHistoryController();
        ReflectionTestUtils.setField(controller, "executionLedgerQueryService", ctx.queryService);
        ReflectionTestUtils.setField(controller, "conversationHistoryReplayService", ctx.replayService);
        ReflectionTestUtils.setField(controller, "conversationSessionAuthorizationService",
                Mockito.mock(ConversationSessionAuthorizationService.class));

        Response<ConversationHistoryPageRespVO> response = controller.detail("session-plan-solve-001", 20, null);

        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        Assert.assertNotNull(response.getData());
        Assert.assertEquals(Boolean.TRUE, response.getData().getDeepThink());
    }

    @Test
    public void shouldDefaultRecentSessionPageToTenAndKeepSummaryOutOfListPayload() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        for (int index = 1; index <= 25; index += 1) {
            seedRun(
                    ctx,
                    String.format("req-limit-%03d", index),
                    String.format("session-limit-%03d", index),
                    index % 2 == 0 ? "file_tool" : "read_tool",
                    String.format("最近会话 %03d", index),
                    LocalDateTime.of(2026, 5, 2, 8, 0, 0).plusMinutes(index),
                    index % 3 == 0 ? ExecutionLedgerConstants.STATUS_FAILED : ExecutionLedgerConstants.STATUS_SUCCESS,
                    "summary-body-" + index,
                    index % 2 == 0 ? "report-limit-" + index + ".md" : null
            );
        }

        AgentConversationHistoryController controller = new AgentConversationHistoryController();
        ReflectionTestUtils.setField(controller, "executionLedgerQueryService", ctx.queryService);
        ReflectionTestUtils.setField(controller, "conversationHistoryReplayService", ctx.replayService);
        ReflectionTestUtils.setField(controller, "conversationSessionAuthorizationService",
                Mockito.mock(ConversationSessionAuthorizationService.class));

        Response<ConversationSessionPageRespVO> response = controller.list(null, null);

        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        Assert.assertNotNull(response.getData());
        Assert.assertEquals(10, response.getData().getSessions().size());
        Assert.assertTrue(response.getData().isHasMore());
        Assert.assertNotNull(response.getData().getNextCursor());
        Assert.assertEquals("session-limit-025", response.getData().getSessions().get(0).getSessionId());
        Assert.assertEquals("最近会话 025", response.getData().getSessions().get(0).getLatestQueryText());
        Assert.assertEquals("session-limit-016", response.getData().getSessions().get(9).getSessionId());
    }

    @Test
    public void shouldExposeMissingArtifactReasonAndStoppedRunStatus() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        seedRun(ctx, "req-history-stop-001", "session-history-stop-001", "file_tool",
                "停止前先生成文件", LocalDateTime.of(2026, 5, 2, 12, 0, 0),
                ExecutionLedgerConstants.STATUS_STOPPED, "summary:req-history-stop-001", "stopped-report.md");

        AgentConversationHistoryController controller = new AgentConversationHistoryController();
        ReflectionTestUtils.setField(controller, "executionLedgerQueryService", ctx.queryService);
        ReflectionTestUtils.setField(controller, "conversationHistoryReplayService", ctx.replayService);
        ReflectionTestUtils.setField(controller, "conversationSessionAuthorizationService",
                Mockito.mock(ConversationSessionAuthorizationService.class));

        Response<ConversationHistoryPageRespVO> response = controller.detail("session-history-stop-001", 20, null);

        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        Assert.assertNotNull(response.getData());
        Assert.assertEquals("STOPPED", response.getData().getStatus());
        Assert.assertEquals("STOPPED", response.getData().getRuns().get(0).getStatus());

        Response<ConversationRunReplayRespVO> replayResponse = controller.replay("req-history-stop-001");
        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), replayResponse.getCode());
        List<AgentStreamResponseVO> replayFrames = replayResponse.getData().getReplayFrames();
        Assert.assertFalse(replayFrames.isEmpty());
        Map<String, Object> firstEventData = eventData(replayFrames.get(0));
        Assert.assertTrue(firstEventData.containsKey("artifactRefs"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> artifactRefs = (List<Map<String, Object>>) firstEventData.get("artifactRefs");
        Assert.assertEquals(1, artifactRefs.size());
        Assert.assertEquals(Boolean.FALSE, artifactRefs.get(0).get("missing"));
    }

    @Test
    public void shouldReturnNullDetailWhenSessionMissing() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        AgentConversationHistoryController controller = new AgentConversationHistoryController();
        ReflectionTestUtils.setField(controller, "executionLedgerQueryService", ctx.queryService);
        ReflectionTestUtils.setField(controller, "conversationHistoryReplayService", ctx.replayService);
        ReflectionTestUtils.setField(controller, "conversationSessionAuthorizationService",
                Mockito.mock(ConversationSessionAuthorizationService.class));

        Response<ConversationHistoryPageRespVO> response = controller.detail("session-missing-001", 20, null);

        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        Assert.assertNull(response.getData());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nestedResultMap(AgentStreamResponseVO frame) {
        Map<String, Object> eventData = (Map<String, Object>) frame.getResultMap().get("eventData");
        return (Map<String, Object>) eventData.get("resultMap");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> eventData(AgentStreamResponseVO frame) {
        return (Map<String, Object>) frame.getResultMap().get("eventData");
    }

    private void seedRun(ExecutionLedgerFixtureFactory.LedgerTestContext ctx,
                         String requestId,
                         String sessionId,
                         String toolName,
                         String queryText,
                         LocalDateTime startedAt,
                         Integer runStatus,
                         String finalSummaryText,
                         String fileName) {
        ToolStructuredOutput structuredOutput = "file_tool".equals(toolName)
                ? FileToolOutput.builder()
                .command("upload")
                .primaryFileName(fileName)
                .fileRefs(fileName == null ? List.of() : List.of(buildFileRef(fileName)))
                .build()
                : null;
        seedRun(
                ctx,
                requestId,
                sessionId,
                ExecutionLedgerConstants.ENTRY_AGENT_REACT,
                toolName,
                queryText,
                startedAt,
                runStatus,
                finalSummaryText,
                structuredOutput,
                fileName
        );
    }

    private void seedRun(ExecutionLedgerFixtureFactory.LedgerTestContext ctx,
                         String requestId,
                         String sessionId,
                         String entryAgent,
                         String toolName,
                         String queryText,
                         LocalDateTime startedAt,
                         Integer runStatus,
                         String finalSummaryText,
                         ToolStructuredOutput structuredOutput,
                         String fileName) {
        Long runId = ctx.recorder.createRun(DialogueRunStartRecord.builder()
                .runUid(requestId)
                .requestId(requestId)
                .sessionId(sessionId)
                .userId("user-test")
                .entryAgent(entryAgent)
                .queryText(queryText)
                .startedAt(startedAt)
                .build());

        Map<String, Long> toolIds = ctx.recorder.createToolInvocations(ToolInvocationBatchStartRecord.builder()
                .runId(runId)
                .requestId(requestId)
                .llmInvocationId(Math.abs((long) requestId.hashCode()))
                .agentName("react")
                .stepNo(1)
                .items(List.of(ToolInvocationBatchStartRecord.Item.builder()
                        .toolCallId(requestId + "-tool-1")
                        .dispatchIndex(1)
                        .toolName(toolName)
                        .toolProvider(ExecutionLedgerConstants.TOOL_PROVIDER_LOCAL)
                        .inputJson("{\"requestId\":\"" + requestId + "\"}")
                        .startedAt(startedAt.plusSeconds(1))
                        .build()))
                .build());
        Long toolInvocationId = toolIds.get(requestId + "-tool-1");
        ctx.recorder.finishToolInvocation(ToolInvocationFinishRecord.builder()
                .toolInvocationId(toolInvocationId)
                .runId(runId)
                .requestId(requestId)
                .sessionId(sessionId)
                .toolCallId(requestId + "-tool-1")
                .toolName(toolName)
                .status(runStatus)
                .llmObservation(runStatus != null && runStatus == ExecutionLedgerConstants.STATUS_SUCCESS ? "done" : "failed")
                .errorMsg(runStatus != null && runStatus == ExecutionLedgerConstants.STATUS_SUCCESS ? null : "tool_failed")
                .structuredOutput(structuredOutput)
                .finishedAt(startedAt.plusSeconds(2))
                .build());

        if (fileName != null) {
            ctx.recorder.recordArtifacts(List.of(ArtifactRecordCommand.builder()
                    .runId(runId)
                    .requestId(requestId)
                    .toolInvocationId(toolInvocationId)
                    .toolCallId(requestId + "-tool-1")
                    .artifactRole(ExecutionLedgerConstants.ARTIFACT_ROLE_OUTPUT)
                    .visibility(ExecutionLedgerConstants.VISIBILITY_VISIBLE)
                    .sourceType(ExecutionLedgerConstants.SOURCE_TYPE_TOOL_OUTPUT)
                    .sourceName(toolName)
                    .fileName(fileName)
                    .storageKey("oss://" + fileName)
                    .downloadUrl("https://file.example.com/download/" + fileName)
                    .previewUrl("https://file.example.com/preview/" + fileName)
                    .build()));
        }

        ctx.recorder.finishRun(DialogueRunFinishRecord.builder()
                .runId(runId)
                .requestId(requestId)
                .status(runStatus)
                .finalSummaryText(finalSummaryText)
                .errorMsg(runStatus != null && runStatus == ExecutionLedgerConstants.STATUS_SUCCESS ? null : "run_failed")
                .finishedAt(startedAt.plusSeconds(3))
                .build());
    }

    private ToolFileRef buildFileRef(String fileName) {
        return ToolFileRef.builder()
                .fileName(fileName)
                .ossUrl("oss://" + fileName)
                .downloadUrl("https://file.example.com/download/" + fileName)
                .previewUrl("https://file.example.com/preview/" + fileName)
                .build();
    }

}
