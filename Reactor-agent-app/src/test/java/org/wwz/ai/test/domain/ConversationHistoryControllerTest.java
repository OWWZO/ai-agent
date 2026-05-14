package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.domain.agent.ledger.model.ArtifactRecordCommand;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunFinishRecord;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunStartRecord;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationBatchStartRecord;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationFinishRecord;
import org.wwz.ai.domain.agent.reactor.model.response.GptProcessResult;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.FileToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ReportToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolFileRef;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolStructuredOutput;
import org.wwz.ai.trigger.http.agent.AgentConversationHistoryController;
import org.wwz.ai.trigger.http.agent.vo.ConversationHistoryDetailRespVO;
import org.wwz.ai.trigger.http.agent.vo.ConversationSessionRespVO;
import org.wwz.ai.types.enums.ResponseCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 会话历史接口回归测试。
 */
public class ConversationHistoryControllerTest {

    @Test
    public void shouldReturnSessionDetailWithStatsAndReplayFrames() {
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

        Response<ConversationHistoryDetailRespVO> response = controller.detail("session-history-001");

        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        Assert.assertNotNull(response.getData());
        ConversationHistoryDetailRespVO detail = response.getData();
        Assert.assertEquals("session-history-001", detail.getSessionId());
        Assert.assertEquals("FAILED", detail.getStatus());
        Assert.assertEquals(Integer.valueOf(2), detail.getRunCount());
        Assert.assertEquals(Integer.valueOf(1), detail.getFinishedRunCount());
        Assert.assertEquals(Integer.valueOf(1), detail.getFailedRunCount());
        Assert.assertNotNull(detail.getRole());
        Assert.assertEquals("默认助手", detail.getRole().getAgentName());
        Assert.assertEquals(2, detail.getRuns().size());
        Assert.assertFalse(detail.getRuns().get(0).getReplayFrames().isEmpty());

        List<GptProcessResult> secondRunFrames = detail.getRuns().get(1).getReplayFrames();
        Assert.assertFalse(secondRunFrames.isEmpty());
        Map<String, Object> finalResultMap = nestedResultMap(secondRunFrames.get(secondRunFrames.size() - 1));
        Assert.assertEquals("result", finalResultMap.get("messageType"));
        Assert.assertEquals("summary:req-history-002", finalResultMap.get("result"));
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

        Response<List<ConversationSessionRespVO>> response = controller.list(20);

        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        Assert.assertNotNull(response.getData());
        Assert.assertEquals(2, response.getData().size());
        Assert.assertEquals("session-list-002", response.getData().get(0).getSessionId());
        Assert.assertEquals("FAILED", response.getData().get(0).getStatus());
        Assert.assertEquals("第二个会话", response.getData().get(0).getLatestQueryText());
        Assert.assertEquals("session-list-001", response.getData().get(1).getSessionId());
        Assert.assertEquals("SUCCESS", response.getData().get(1).getStatus());
    }

    @Test
    public void shouldRestoreStructuredReactSessionModeFromHistoryDetail() {
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
                ReportToolOutput.builder()
                        .fileType("html")
                        .content("<html><body>网页报告</body></html>")
                        .fileRefs(List.of(buildFileRef(fileName)))
                        .build(),
                fileName
        );

        AgentConversationHistoryController controller = new AgentConversationHistoryController();
        ReflectionTestUtils.setField(controller, "executionLedgerQueryService", ctx.queryService);
        ReflectionTestUtils.setField(controller, "conversationHistoryReplayService", ctx.replayService);

        Response<ConversationHistoryDetailRespVO> response = controller.detail("session-react-structured-001");

        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        Assert.assertNotNull(response.getData());
        Assert.assertEquals("html", response.getData().getOutputStyle());
        Assert.assertEquals(Boolean.FALSE, response.getData().getDeepThink());
    }

    @Test
    public void shouldRestorePlanSolveHistoryAsDeepResearchMode() {
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
                ReportToolOutput.builder()
                        .fileType("html")
                        .content("<html><body>深度研究报告</body></html>")
                        .fileRefs(List.of(buildFileRef(fileName)))
                        .build(),
                fileName
        );

        AgentConversationHistoryController controller = new AgentConversationHistoryController();
        ReflectionTestUtils.setField(controller, "executionLedgerQueryService", ctx.queryService);
        ReflectionTestUtils.setField(controller, "conversationHistoryReplayService", ctx.replayService);

        Response<ConversationHistoryDetailRespVO> response = controller.detail("session-plan-solve-001");

        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        Assert.assertNotNull(response.getData());
        Assert.assertEquals("html", response.getData().getOutputStyle());
        Assert.assertEquals(Boolean.TRUE, response.getData().getDeepThink());
    }

    @Test
    public void shouldLimitRecentSessionsToTwentyAndKeepSummaryOutOfListPayload() {
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

        Response<List<ConversationSessionRespVO>> response = controller.list(null);

        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        Assert.assertNotNull(response.getData());
        Assert.assertEquals(20, response.getData().size());
        Assert.assertEquals("session-limit-025", response.getData().get(0).getSessionId());
        Assert.assertEquals("最近会话 025", response.getData().get(0).getLatestQueryText());
        Assert.assertEquals("session-limit-006", response.getData().get(19).getSessionId());
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

        Response<ConversationHistoryDetailRespVO> response = controller.detail("session-history-stop-001");

        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        Assert.assertNotNull(response.getData());
        Assert.assertEquals("STOPPED", response.getData().getStatus());
        Assert.assertEquals("STOPPED", response.getData().getRuns().get(0).getStatus());
        List<GptProcessResult> replayFrames = response.getData().getRuns().get(0).getReplayFrames();
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

        Response<ConversationHistoryDetailRespVO> response = controller.detail("session-missing-001");

        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        Assert.assertNull(response.getData());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nestedResultMap(GptProcessResult frame) {
        Map<String, Object> eventData = (Map<String, Object>) frame.getResultMap().get("eventData");
        return (Map<String, Object>) eventData.get("resultMap");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> eventData(GptProcessResult frame) {
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
