package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.ledger.model.ArtifactRecordCommand;
import org.wwz.ai.domain.agent.ledger.model.ConversationHistoryDetail;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunFinishRecord;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunStartRecord;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.model.ExecutionRunDetail;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationBatchStartRecord;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationFinishRecord;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationView;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.FileToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolFileRef;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 执行账本查询服务测试。
 */
public class ExecutionLedgerQueryServiceTest {

    @Test
    public void shouldQueryRunDetailRecentToolsAndRecentSessionRuns() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        seedRun(ctx, "req-query-001", "session-query-001", "visitor-query-001", "file_tool", 1, "report-1.md");
        seedRun(ctx, "req-query-002", "session-query-001", "visitor-query-001", "file_tool", 2, "report-2.md");

        ExecutionRunDetail detail = ctx.queryService.queryRunDetail("req-query-001");
        Assert.assertNotNull(detail);
        Assert.assertEquals("req-query-001", detail.getRun().getRequestId());
        Assert.assertEquals(1, detail.getToolInvocations().size());
        Assert.assertEquals(1, detail.getArtifacts().size());
        Assert.assertTrue(detail.getToolInvocations().get(0).getStructuredOutput() instanceof FileToolOutput);
        Assert.assertEquals("report-1.md", detail.getArtifacts().get(0).getFileName());
        Assert.assertEquals("req-query-001", detail.getArtifacts().get(0).getRequestId());

        List<ToolInvocationView> recentTools = ctx.queryService.queryRecentToolInvocations("file_tool", 100);
        Assert.assertEquals(2, recentTools.size());
        Assert.assertEquals("req-query-002", recentTools.get(0).getRequestId());
        Assert.assertEquals(Integer.valueOf(1), recentTools.get(0).getArtifactCount());
        Assert.assertTrue(recentTools.get(0).getStructuredOutput() instanceof FileToolOutput);

        var recentRuns = ctx.queryService.queryRecentSessionRuns("session-query-001", 10);
        Assert.assertEquals(2, recentRuns.size());
        Assert.assertEquals("req-query-002", recentRuns.get(0).getRequestId());
        Assert.assertEquals(1, recentRuns.get(0).getArtifactSummaries().size());
        Assert.assertEquals("report-2.md", recentRuns.get(0).getArtifactSummaries().get(0).getFileName());

        var orderedRuns = ctx.queryService.querySessionRuns("session-query-001");
        Assert.assertEquals(2, orderedRuns.size());
        Assert.assertEquals("req-query-001", orderedRuns.get(0).getRequestId());
        Assert.assertEquals("req-query-002", orderedRuns.get(1).getRequestId());

        var sessionView = ctx.queryService.querySession("session-query-001");
        Assert.assertNotNull(sessionView);
        Assert.assertEquals("seed:req-query-002", sessionView.getLatestQueryText());
        Assert.assertEquals(Integer.valueOf(2), sessionView.getRunCount());

        var recentSessions = ctx.queryService.queryRecentSessions(20);
        Assert.assertEquals(1, recentSessions.size());
        Assert.assertEquals("session-query-001", recentSessions.get(0).getSessionId());
    }

    @Test
    public void shouldKeepFailedRichToolExplainableWithMinimalStructuredOutput() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        LocalDateTime now = LocalDateTime.now();
        Long runId = ctx.recorder.createRun(DialogueRunStartRecord.builder()
                .runUid("req-query-failed-001")
                .requestId("req-query-failed-001")
                .sessionId("session-query-failed-001")
                .entryAgent(ExecutionLedgerConstants.ENTRY_AGENT_REACT)
                .queryText("seed:req-query-failed-001")
                .startedAt(now)
                .build());

        Long toolInvocationId = ctx.recorder.createToolInvocations(ToolInvocationBatchStartRecord.builder()
                .runId(runId)
                .requestId("req-query-failed-001")
                .llmInvocationId(901L)
                .agentName("react")
                .stepNo(1)
                .items(List.of(ToolInvocationBatchStartRecord.Item.builder()
                        .toolCallId("tool-call-failed-001")
                        .dispatchIndex(1)
                        .toolName("file_tool")
                        .toolProvider(ExecutionLedgerConstants.TOOL_PROVIDER_LOCAL)
                        .inputJson("{\"dispatch\":1}")
                        .startedAt(now.plusSeconds(1))
                        .build()))
                .build())
                .get("tool-call-failed-001");
        ctx.recorder.finishToolInvocation(ToolInvocationFinishRecord.builder()
                .toolInvocationId(toolInvocationId)
                .runId(runId)
                .requestId("req-query-failed-001")
                .sessionId("session-query-failed-001")
                .toolCallId("tool-call-failed-001")
                .toolName("file_tool")
                .status(ExecutionLedgerConstants.STATUS_FAILED)
                .llmObservation("上游报告文件生成超时")
                .errorMsg("timeout")
                .structuredOutput(FileToolOutput.builder()
                        .command("upload")
                        .fileRefs(List.of())
                        .build())
                .finishedAt(now.plusSeconds(2))
                .build());

        ExecutionRunDetail detail = ctx.queryService.queryRunDetail("req-query-failed-001");
        ToolInvocationView toolInvocation = detail.getToolInvocations().get(0);
        FileToolOutput structuredOutput = (FileToolOutput) toolInvocation.getStructuredOutput();

        Assert.assertEquals(Integer.valueOf(ExecutionLedgerConstants.STATUS_FAILED), toolInvocation.getStatus());
        Assert.assertEquals("timeout", toolInvocation.getErrorMsg());
        Assert.assertEquals("上游报告文件生成超时", toolInvocation.getLlmObservation());
        Assert.assertNotNull(structuredOutput);
        Assert.assertTrue(structuredOutput.getFileRefs().isEmpty());
    }

    @Test
    public void shouldBuildConversationHistoryWithSummaryFallback() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        seedRun(ctx, "req-history-001", "session-history-001", "visitor-history-001", "file_tool", 1, "report-1.md");
        seedRun(ctx, "req-history-002", "session-history-001", "visitor-history-001", "read_tool", 2, "report-2.md");

        ConversationHistoryDetail detail = ctx.replayService.queryConversationHistory("session-history-001");

        Assert.assertNotNull(detail);
        Assert.assertEquals("session-history-001", detail.getSessionId());
        Assert.assertEquals(2, detail.getRuns().size());
        Assert.assertEquals("req-history-001", detail.getRuns().get(0).getRequestId());
        Assert.assertEquals("req-history-002", detail.getRuns().get(1).getRequestId());
        Assert.assertFalse(detail.getRuns().get(0).getReplayFrames().isEmpty());
        Assert.assertFalse(detail.getRuns().get(1).getReplayFrames().isEmpty());
    }

    @Test
    public void shouldNormalizeRecentSessionLimitToTwentyAndKeepLatestOrder() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        for (int index = 1; index <= 25; index += 1) {
            seedRun(
                    ctx,
                    String.format("req-session-limit-%03d", index),
                    String.format("session-limit-%03d", index),
                    String.format("visitor-limit-%03d", index),
                    "file_tool",
                    index,
                    "report-limit-" + index + ".md"
            );
        }

        var recentSessions = ctx.queryService.queryRecentSessions(0);

        Assert.assertEquals(20, recentSessions.size());
        Assert.assertEquals("session-limit-025", recentSessions.get(0).getSessionId());
        Assert.assertEquals("session-limit-006", recentSessions.get(19).getSessionId());
        Assert.assertEquals("seed:req-session-limit-025", recentSessions.get(0).getLatestQueryText());
        Assert.assertEquals(Integer.valueOf(1), recentSessions.get(0).getRunCount());
    }

    @Test
    public void shouldFilterSessionQueriesByVisitorOwnership() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        seedRun(ctx, "req-visitor-001", "session-visitor-001", "visitor-001", "file_tool", 1, "visitor-001.md");
        seedRun(ctx, "req-visitor-002", "session-visitor-002", "visitor-002", "file_tool", 2, "visitor-002.md");

        Assert.assertNotNull(ctx.queryService.querySession("visitor-001", "session-visitor-001"));
        Assert.assertNull(ctx.queryService.querySession("visitor-002", "session-visitor-001"));

        List<?> visitorOneSessions = ctx.queryService.queryRecentSessions("visitor-001", 20);
        List<?> visitorTwoSessions = ctx.queryService.queryRecentSessions("visitor-002", 20);

        Assert.assertEquals(1, visitorOneSessions.size());
        Assert.assertEquals(1, visitorTwoSessions.size());
        Assert.assertEquals("session-visitor-001", ctx.queryService.queryRecentSessions("visitor-001", 20).get(0).getSessionId());
        Assert.assertEquals("session-visitor-002", ctx.queryService.queryRecentSessions("visitor-002", 20).get(0).getSessionId());
        Assert.assertTrue(ctx.queryService.queryRecentSessions("visitor-003", 20).isEmpty());
    }

    private void seedRun(ExecutionLedgerFixtureFactory.LedgerTestContext ctx,
                         String requestId,
                         String sessionId,
                         String visitorId,
                         String toolName,
                         int dispatchIndex,
                         String fileName) {
        LocalDateTime now = LocalDateTime.now().plusSeconds(dispatchIndex);
        Long runId = ctx.recorder.createRun(DialogueRunStartRecord.builder()
                .runUid(requestId)
                .requestId(requestId)
                .sessionId(sessionId)
                .visitorId(visitorId)
                .entryAgent(ExecutionLedgerConstants.ENTRY_AGENT_REACT)
                .queryText("seed:" + requestId)
                .startedAt(now)
                .build());

        Map<String, Long> toolIds = ctx.recorder.createToolInvocations(ToolInvocationBatchStartRecord.builder()
                .runId(runId)
                .requestId(requestId)
                .llmInvocationId(100L + dispatchIndex)
                .agentName("react")
                .stepNo(dispatchIndex)
                .items(List.of(ToolInvocationBatchStartRecord.Item.builder()
                        .toolCallId("tool-call-" + dispatchIndex)
                        .dispatchIndex(dispatchIndex)
                        .toolName(toolName)
                        .toolProvider(ExecutionLedgerConstants.TOOL_PROVIDER_LOCAL)
                        .inputJson("{\"dispatch\":" + dispatchIndex + "}")
                        .startedAt(now.plusSeconds(1))
                        .build()))
                .build());
        Long toolInvocationId = toolIds.get("tool-call-" + dispatchIndex);
        ctx.recorder.finishToolInvocation(ToolInvocationFinishRecord.builder()
                .toolInvocationId(toolInvocationId)
                .runId(runId)
                .requestId(requestId)
                .sessionId(sessionId)
                .toolCallId("tool-call-" + dispatchIndex)
                .toolName(toolName)
                .status(ExecutionLedgerConstants.STATUS_SUCCESS)
                .llmObservation("done")
                .structuredOutput(FileToolOutput.builder()
                        .command("upload")
                        .primaryFileName(fileName)
                        .fileRefs(List.of(ToolFileRef.builder()
                                .fileName(fileName)
                                .ossUrl("oss://" + fileName)
                                .downloadUrl("oss://" + fileName)
                                .previewUrl("oss://" + fileName)
                                .build()))
                        .build())
                .finishedAt(now.plusSeconds(2))
                .build());
        ctx.recorder.recordArtifacts(List.of(ArtifactRecordCommand.builder()
                .runId(runId)
                .requestId(requestId)
                .toolInvocationId(toolInvocationId)
                .toolCallId("tool-call-" + dispatchIndex)
                .artifactRole(ExecutionLedgerConstants.ARTIFACT_ROLE_OUTPUT)
                .visibility(ExecutionLedgerConstants.VISIBILITY_VISIBLE)
                .sourceType(ExecutionLedgerConstants.SOURCE_TYPE_TOOL_OUTPUT)
                .sourceName(toolName)
                .fileName(fileName)
                .storageKey("oss://" + fileName)
                .downloadUrl("oss://" + fileName)
                .previewUrl("oss://" + fileName)
                .build()));
        ctx.recorder.finishRun(DialogueRunFinishRecord.builder()
                .runId(runId)
                .requestId(requestId)
                .status(ExecutionLedgerConstants.STATUS_SUCCESS)
                .finalSummaryText("summary:" + requestId)
                .finishedAt(now.plusSeconds(3))
                .build());
    }
}
