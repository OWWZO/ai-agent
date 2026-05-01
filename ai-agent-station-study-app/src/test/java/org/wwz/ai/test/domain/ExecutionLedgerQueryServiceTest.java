package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.reactor.model.ledger.ArtifactRecordCommand;
import org.wwz.ai.domain.agent.reactor.model.ledger.DialogueRunFinishRecord;
import org.wwz.ai.domain.agent.reactor.model.ledger.DialogueRunStartRecord;
import org.wwz.ai.domain.agent.reactor.model.ledger.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.reactor.model.ledger.ExecutionRunDetail;
import org.wwz.ai.domain.agent.reactor.model.ledger.ToolInvocationBatchStartRecord;
import org.wwz.ai.domain.agent.reactor.model.ledger.ToolInvocationFinishRecord;
import org.wwz.ai.domain.agent.reactor.model.ledger.ToolInvocationView;

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
        seedRun(ctx, "req-query-001", "session-query-001", "file_tool", 1, "report-1.md");
        seedRun(ctx, "req-query-002", "session-query-001", "file_tool", 2, "report-2.md");

        ExecutionRunDetail detail = ctx.queryService.queryRunDetail("req-query-001");
        Assert.assertNotNull(detail);
        Assert.assertEquals("req-query-001", detail.getRun().getRequestId());
        Assert.assertEquals(1, detail.getToolInvocations().size());
        Assert.assertEquals(1, detail.getArtifacts().size());
        Assert.assertTrue(detail.getToolInvocations().get(0).getOutputJson().contains("\"schemaVersion\":1"));
        Assert.assertEquals("report-1.md", detail.getArtifacts().get(0).getFileName());

        List<ToolInvocationView> recentTools = ctx.queryService.queryRecentToolInvocations("file_tool", 100);
        Assert.assertEquals(2, recentTools.size());
        Assert.assertEquals("req-query-002", recentTools.get(0).getRequestId());
        Assert.assertEquals(Integer.valueOf(1), recentTools.get(0).getArtifactCount());

        var recentRuns = ctx.queryService.queryRecentSessionRuns("session-query-001", 10);
        Assert.assertEquals(2, recentRuns.size());
        Assert.assertEquals("req-query-002", recentRuns.get(0).getRequestId());
        Assert.assertEquals(1, recentRuns.get(0).getArtifactSummaries().size());
        Assert.assertEquals("report-2.md", recentRuns.get(0).getArtifactSummaries().get(0).getFileName());
    }

    private void seedRun(ExecutionLedgerFixtureFactory.LedgerTestContext ctx,
                         String requestId,
                         String sessionId,
                         String toolName,
                         int dispatchIndex,
                         String fileName) {
        LocalDateTime now = LocalDateTime.now().plusSeconds(dispatchIndex);
        Long runId = ctx.recorder.createRun(DialogueRunStartRecord.builder()
                .runUid(requestId)
                .requestId(requestId)
                .sessionId(sessionId)
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
                .requestId(requestId)
                .toolCallId("tool-call-" + dispatchIndex)
                .status(ExecutionLedgerConstants.STATUS_SUCCESS)
                .llmObservation("done")
                .outputJson("{\"schemaVersion\":1,\"resultType\":\"plain_text\",\"data\":{\"text\":\"done\"}}")
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
