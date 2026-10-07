package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.ledger.model.ArtifactRecordCommand;
import org.wwz.ai.domain.agent.ledger.model.ConversationSessionCursor;
import org.wwz.ai.domain.agent.ledger.model.ArtifactView;
import org.wwz.ai.domain.agent.ledger.model.ConversationHistoryDetail;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunFinishRecord;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunStartRecord;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.model.ExecutionRunDetail;
import org.wwz.ai.domain.agent.ledger.model.LlmInvocationFinishRecord;
import org.wwz.ai.domain.agent.ledger.model.LlmInvocationStartRecord;
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
        seedRun(ctx, "req-query-001", "session-query-001", "user-query-001", "file_tool", 1, "report-1.md");
        seedRun(ctx, "req-query-002", "session-query-001", "user-query-001", "file_tool", 2, "report-2.md");

        ExecutionRunDetail detail = ctx.queryService.queryRunDetail("req-query-001");
        Assert.assertNotNull(detail);
        Assert.assertEquals("req-query-001", detail.getRun().getRequestId());
        Assert.assertEquals(1, detail.getToolInvocations().size());
        Assert.assertEquals(1, detail.getArtifacts().size());
        Assert.assertNull(detail.getToolInvocations().get(0).getStructuredOutput());
        Assert.assertEquals("report-1.md", detail.getArtifacts().get(0).getFileName());
        Assert.assertEquals("req-query-001", detail.getArtifacts().get(0).getRequestId());

        List<ToolInvocationView> recentTools = ctx.queryService.queryRecentToolInvocations("file_tool", 100);
        Assert.assertEquals(2, recentTools.size());
        Assert.assertEquals("req-query-002", recentTools.get(0).getRequestId());
        Assert.assertEquals(Integer.valueOf(1), recentTools.get(0).getArtifactCount());
        Assert.assertNull(recentTools.get(0).getStructuredOutput());

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
    public void shouldPageSessionRunsAndClampPageSize() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        seedRun(ctx, "req-page-001", "session-page-001", "user-page-001", "file_tool", 1, "report-1.md");
        seedRun(ctx, "req-page-002", "session-page-001", "user-page-001", "file_tool", 2, "report-2.md");

        var firstPage = ctx.queryService.querySessionRuns("session-page-001", -10, 1_000);
        Assert.assertEquals(2, firstPage.size());
        Assert.assertEquals("req-page-001", firstPage.get(0).getRequestId());

        var secondPage = ctx.queryService.querySessionRuns("session-page-001", 1, 1);
        Assert.assertEquals(1, secondPage.size());
        Assert.assertEquals("req-page-002", secondPage.get(0).getRequestId());
    }

    @Test
    public void shouldKeepFailedRetiredToolExplainableWithObservation() {
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
        Assert.assertEquals(Integer.valueOf(ExecutionLedgerConstants.STATUS_FAILED), toolInvocation.getStatus());
        Assert.assertEquals("timeout", toolInvocation.getErrorMsg());
        Assert.assertEquals("上游报告文件生成超时", toolInvocation.getLlmObservation());
        Assert.assertNull(toolInvocation.getStructuredOutput());
    }

    @Test
    public void shouldBuildConversationHistoryWithSummaryFallback() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        seedRun(ctx, "req-history-001", "session-history-001", "user-history-001", "file_tool", 1, "report-1.md");
        seedRun(ctx, "req-history-002", "session-history-001", "user-history-001", "read_tool", 2, "report-2.md");

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
    public void shouldBatchLedgerFactsAndRichOutputsForConversationHistory() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        seedRun(ctx, "req-batch-history-001", "session-batch-history-001", "user-batch-001", "deep_search", 1, "report-1.md");
        seedRun(ctx, "req-batch-history-002", "session-batch-history-001", "user-batch-001", "file_tool", 2, "report-2.md");
        seedRun(ctx, "req-batch-history-003", "session-batch-history-001", "user-batch-001", "read_tool", 3, "report-3.md");
        int queryRunByRequestIdBefore = ctx.store.queryRunByRequestIdCount;
        int queryLlmByRunIdBefore = ctx.store.queryLlmByRunIdCount;
        int queryToolByRunIdBefore = ctx.store.queryToolByRunIdCount;
        int queryArtifactsByRunIdBefore = ctx.store.queryArtifactsByRunIdCount;
        int readToolOutputByInvocationIdBefore = ctx.store.readToolOutputByInvocationIdCount;

        ConversationHistoryDetail detail = ctx.replayService.queryConversationHistory("session-batch-history-001");

        Assert.assertEquals(3, detail.getRuns().size());
        Assert.assertEquals(queryRunByRequestIdBefore, ctx.store.queryRunByRequestIdCount);
        Assert.assertEquals(queryLlmByRunIdBefore, ctx.store.queryLlmByRunIdCount);
        Assert.assertEquals(1, ctx.store.queryLlmByRunIdsCount);
        Assert.assertEquals(queryToolByRunIdBefore, ctx.store.queryToolByRunIdCount);
        Assert.assertEquals(1, ctx.store.queryToolByRunIdsCount);
        Assert.assertEquals(queryArtifactsByRunIdBefore, ctx.store.queryArtifactsByRunIdCount);
        Assert.assertEquals(1, ctx.store.queryArtifactsByRunIdsCount);
        Assert.assertEquals(readToolOutputByInvocationIdBefore, ctx.store.readToolOutputByInvocationIdCount);
        Assert.assertEquals(1, ctx.store.readToolOutputByInvocationIdsCount);
    }

    @Test
    public void shouldRestoreLatestContextUsageFromLlmLedger() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        LocalDateTime now = LocalDateTime.now();
        Long runId = ctx.recorder.createRun(DialogueRunStartRecord.builder()
                .runUid("req-context-history-001")
                .requestId("req-context-history-001")
                .sessionId("session-context-history-001")
                .entryAgent(ExecutionLedgerConstants.ENTRY_AGENT_REACT)
                .queryText("恢复上下文")
                .startedAt(now)
                .build());
        Long invocationId = ctx.recorder.createLlmInvocation(LlmInvocationStartRecord.builder()
                .runId(runId)
                .requestId("req-context-history-001")
                .invocationSeq(1)
                .agentName("react")
                .callKind("askTool")
                .streaming(false)
                .modelName("test-model")
                .estTotalTokens(12800)
                .estSystemTokens(1200)
                .estMessageTokens(9000)
                .estToolTokens(2600)
                .startedAt(now)
                .build());
        ctx.recorder.finishLlmInvocation(LlmInvocationFinishRecord.builder()
                .llmInvocationId(invocationId)
                .requestId("req-context-history-001")
                .status(ExecutionLedgerConstants.STATUS_SUCCESS)
                .promptTokens(13100)
                .completionTokens(700)
                .totalTokens(13800)
                .finishedAt(now.plusSeconds(1))
                .build());

        ConversationHistoryDetail detail = ctx.replayService.queryConversationHistory("session-context-history-001");

        Assert.assertNotNull(detail.getRuns().get(0).getContextUsage());
        Assert.assertEquals(1200, detail.getRuns().get(0).getContextUsage().getSys());
        Assert.assertEquals(2600, detail.getRuns().get(0).getContextUsage().getTools());
        Assert.assertEquals(9000, detail.getRuns().get(0).getContextUsage().getHistory());
        Assert.assertEquals(13100, detail.getRuns().get(0).getContextUsage().getUsed());
        Assert.assertEquals("measured", detail.getRuns().get(0).getContextUsage().getSource());
    }

    @Test
    public void shouldNormalizeRecentSessionLimitToTwentyAndKeepLatestOrder() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        for (int index = 1; index <= 25; index += 1) {
            seedRun(
                    ctx,
                    String.format("req-session-limit-%03d", index),
                    String.format("session-limit-%03d", index),
                    String.format("user-limit-%03d", index),
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
    public void shouldFilterSessionQueriesByUserIdOwnership() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        seedRun(ctx, "req-user-001", "session-user-001", "user-001", "file_tool", 1, "user-001.md");
        seedRun(ctx, "req-user-002", "session-user-002", "user-002", "file_tool", 2, "user-002.md");

        Assert.assertNotNull(ctx.queryService.querySession("user-001", "session-user-001"));
        Assert.assertNull(ctx.queryService.querySession("user-002", "session-user-001"));

        List<?> userOneSessions = ctx.queryService.queryRecentSessions("user-001", 20);
        List<?> userTwoSessions = ctx.queryService.queryRecentSessions("user-002", 20);

        Assert.assertEquals(1, userOneSessions.size());
        Assert.assertEquals(1, userTwoSessions.size());
        Assert.assertEquals("session-user-001", ctx.queryService.queryRecentSessions("user-001", 20).get(0).getSessionId());
        Assert.assertEquals("session-user-002", ctx.queryService.queryRecentSessions("user-002", 20).get(0).getSessionId());
        Assert.assertTrue(ctx.queryService.queryRecentSessions("user-003", 20).isEmpty());
    }

    @Test
    public void shouldPageRecentSessionsAcrossEqualAndNullActivityValues() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        LocalDateTime sharedActivity = LocalDateTime.of(2026, 6, 1, 10, 0, 0);
        for (int index = 1; index <= 13; index += 1) {
            String sessionId = String.format("session-page-%03d", index);
            seedRun(
                    ctx,
                    String.format("req-page-%03d", index),
                    sessionId,
                    "user-page",
                    "file_tool",
                    index,
                    "page-" + index + ".md"
            );
            var session = ctx.store.sessions.values().stream()
                    .filter(item -> sessionId.equals(item.getSessionId()))
                    .findFirst()
                    .orElseThrow();
            session.setLastActiveAt(index <= 7 ? sharedActivity : null);
            if (index == 6) {
                session.setDeleted(1);
            }
        }
        seedRun(
                ctx,
                "req-page-outsider",
                "session-page-outsider",
                "user-other",
                "file_tool",
                14,
                "outsider.md"
        );

        List<String> sessionIds = new java.util.ArrayList<>();
        ConversationSessionCursor cursor = null;
        boolean hasMore;
        do {
            var page = ctx.queryService.queryRecentSessions("user-page", cursor, 3);
            sessionIds.addAll(page.getSessions().stream()
                    .map(session -> session.getSessionId())
                    .toList());
            hasMore = page.isHasMore();
            cursor = hasMore ? ConversationSessionCursor.decode(page.getNextCursor()) : null;
            if (hasMore) {
                Assert.assertNotNull(page.getNextCursor());
            } else {
                Assert.assertNull(page.getNextCursor());
            }
        } while (hasMore);

        List<String> expected = new java.util.ArrayList<>();
        for (int index = 7; index >= 1; index -= 1) {
            if (index != 6) {
                expected.add(String.format("session-page-%03d", index));
            }
        }
        for (int index = 13; index >= 8; index -= 1) {
            expected.add(String.format("session-page-%03d", index));
        }
        Assert.assertEquals(expected, sessionIds);
    }

    @Test
    public void shouldQueryVisibleInputAndOutputArtifactsBySessionWithoutLoadingReplayFacts() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        seedRun(ctx, "req-files-001", "session-files-001", "user-files-001", "file_tool", 1, "report-1.md");
        seedRun(ctx, "req-files-002", "session-files-001", "user-files-001", "file_tool", 2, "report-2.md");

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
                        .downloadUrl("https://files.test/download/input.csv")
                        .previewUrl("https://files.test/preview/input.csv")
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
                .filter(artifact -> "deleted.md".equals(artifact.getFileName()))
                .findFirst()
                .orElseThrow()
                .setDeleted(1);

        List<ArtifactView> artifacts = ctx.queryService.querySessionArtifacts("session-files-001");

        Assert.assertEquals(3, artifacts.size());
        Assert.assertEquals("report-1.md", artifacts.get(0).getFileName());
        Assert.assertEquals("report-2.md", artifacts.get(1).getFileName());
        Assert.assertEquals("input.csv", artifacts.get(2).getFileName());
        Assert.assertEquals(1, ctx.store.queryArtifactsBySessionIdCount);
        Assert.assertEquals(0, ctx.store.queryLlmByRunIdsCount);
        Assert.assertEquals(0, ctx.store.queryToolByRunIdsCount);
        Assert.assertEquals(0, ctx.store.readToolOutputByInvocationIdsCount);
    }

    private void seedRun(ExecutionLedgerFixtureFactory.LedgerTestContext ctx,
                         String requestId,
                         String sessionId,
                         String userId,
                         String toolName,
                         int dispatchIndex,
                         String fileName) {
        LocalDateTime now = LocalDateTime.now().plusSeconds(dispatchIndex);
        Long runId = ctx.recorder.createRun(DialogueRunStartRecord.builder()
                .runUid(requestId)
                .requestId(requestId)
                .sessionId(sessionId)
                .userId(userId)
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
