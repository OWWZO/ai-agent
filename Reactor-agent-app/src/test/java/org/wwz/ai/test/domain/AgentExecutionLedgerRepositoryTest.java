package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.ledger.entity.ArtifactRecord;
import org.wwz.ai.domain.agent.ledger.entity.DialogueRun;
import org.wwz.ai.domain.agent.ledger.model.ArtifactRecordCommand;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunFinishRecord;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunStartRecord;
import org.wwz.ai.domain.agent.ledger.model.DialogueSessionUpsertRecord;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.model.LlmInvocationFinishRecord;
import org.wwz.ai.domain.agent.ledger.model.LlmInvocationStartRecord;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationBatchStartRecord;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationFinishRecord;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.FileToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ReportToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolFileRef;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolOutputPersistCommand;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolOutputView;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 执行账本仓储回归测试。
 */
public class AgentExecutionLedgerRepositoryTest {

    @Test
    public void shouldCreateAndFinishLedgerWithDuplicateArtifactsIgnored() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        LocalDateTime now = LocalDateTime.now();

        Long runId = ctx.recorder.createRun(DialogueRunStartRecord.builder()
                .runUid("req-ledger-001")
                .requestId("req-ledger-001")
                .sessionId("session-ledger-001")
                .entryAgent(ExecutionLedgerConstants.ENTRY_AGENT_REACT)
                .queryText("验证完整账本")
                .startedAt(now)
                .build());
        Assert.assertNotNull(runId);

        Long llmInvocationId = ctx.recorder.createLlmInvocation(LlmInvocationStartRecord.builder()
                .runId(runId)
                .requestId("req-ledger-001")
                .invocationSeq(1)
                .agentName("react")
                .stepNo(1)
                .callKind(ExecutionLedgerConstants.CALL_KIND_ASK_TOOL)
                .streaming(false)
                .modelName("test-model")
                .startedAt(now.plusSeconds(1))
                .build());
        Assert.assertNotNull(llmInvocationId);

        ctx.recorder.finishLlmInvocation(LlmInvocationFinishRecord.builder()
                .llmInvocationId(llmInvocationId)
                .requestId("req-ledger-001")
                .status(ExecutionLedgerConstants.STATUS_SUCCESS)
                .responseText("准备调用 file_tool")
                .toolCallCount(1)
                .promptTokens(10)
                .completionTokens(20)
                .totalTokens(30)
                .finishReason("tool_calls")
                .finishedAt(now.plusSeconds(2))
                .build());

        Map<String, Long> toolInvocationIds = ctx.recorder.createToolInvocations(ToolInvocationBatchStartRecord.builder()
                .runId(runId)
                .requestId("req-ledger-001")
                .llmInvocationId(llmInvocationId)
                .agentName("react")
                .stepNo(1)
                .items(List.of(ToolInvocationBatchStartRecord.Item.builder()
                        .toolCallId("tool-call-001")
                        .dispatchIndex(1)
                        .toolName("file_tool")
                        .toolProvider(ExecutionLedgerConstants.TOOL_PROVIDER_LOCAL)
                        .inputJson("{\"path\":\"report.md\"}")
                        .startedAt(now.plusSeconds(2))
                        .build()))
                .build());
        Assert.assertEquals(1, toolInvocationIds.size());

        Long toolInvocationId = toolInvocationIds.get("tool-call-001");
        ctx.recorder.finishToolInvocation(ToolInvocationFinishRecord.builder()
                .toolInvocationId(toolInvocationId)
                .runId(runId)
                .requestId("req-ledger-001")
                .sessionId("session-ledger-001")
                .toolCallId("tool-call-001")
                .toolName("file_tool")
                .status(ExecutionLedgerConstants.STATUS_SUCCESS)
                .llmObservation("生成 report.md")
                .structuredOutput(FileToolOutput.builder()
                        .command("upload")
                        .primaryFileName("report.md")
                        .fileRefs(List.of(ToolFileRef.builder()
                                .fileName("report.md")
                                .ossUrl("oss://report.md")
                                .downloadUrl("oss://report.md")
                                .previewUrl("oss://report.md")
                                .build()))
                        .build())
                .finishedAt(now.plusSeconds(3))
                .build());

        ctx.recorder.recordArtifacts(List.of(
                ArtifactRecordCommand.builder()
                        .runId(runId)
                        .requestId("req-ledger-001")
                        .toolInvocationId(toolInvocationId)
                        .toolCallId("tool-call-001")
                        .artifactRole(ExecutionLedgerConstants.ARTIFACT_ROLE_OUTPUT)
                        .visibility(ExecutionLedgerConstants.VISIBILITY_VISIBLE)
                        .sourceType(ExecutionLedgerConstants.SOURCE_TYPE_TOOL_OUTPUT)
                        .sourceName("file_tool")
                        .fileName("report.md")
                        .storageKey("oss://report.md")
                        .downloadUrl("oss://report.md")
                        .previewUrl("oss://report.md")
                        .metadataJson("{\"kind\":\"report\"}")
                        .build(),
                ArtifactRecordCommand.builder()
                        .runId(runId)
                        .requestId("req-ledger-001")
                        .toolInvocationId(toolInvocationId)
                        .toolCallId("tool-call-001")
                        .artifactRole(ExecutionLedgerConstants.ARTIFACT_ROLE_OUTPUT)
                        .visibility(ExecutionLedgerConstants.VISIBILITY_VISIBLE)
                        .sourceType(ExecutionLedgerConstants.SOURCE_TYPE_TOOL_OUTPUT)
                        .sourceName("file_tool")
                        .fileName("report.md")
                        .storageKey("oss://report.md")
                        .downloadUrl("oss://report.md")
                        .previewUrl("oss://report.md")
                        .metadataJson("{\"kind\":\"report\"}")
                        .build()));

        ctx.recorder.finishRun(DialogueRunFinishRecord.builder()
                .runId(runId)
                .requestId("req-ledger-001")
                .status(ExecutionLedgerConstants.STATUS_SUCCESS)
                .finalSummaryText("报告生成完成")
                .finishedAt(now.plusSeconds(4))
                .build());

        DialogueRun run = ctx.runDao.queryByRequestId("req-ledger-001");
        Assert.assertEquals(Integer.valueOf(1), run.getLlmCallCount());
        Assert.assertEquals(Integer.valueOf(1), run.getToolCallCount());
        Assert.assertEquals(Integer.valueOf(1), run.getArtifactCount());
        Assert.assertEquals(Integer.valueOf(10), run.getPromptTokensTotal());
        Assert.assertEquals(Integer.valueOf(20), run.getCompletionTokensTotal());
        Assert.assertEquals(Integer.valueOf(30), run.getTotalTokensTotal());
        Assert.assertEquals(Integer.valueOf(ExecutionLedgerConstants.STATUS_SUCCESS), run.getStatus());

        List<ArtifactRecord> artifacts = ctx.artifactDao.queryByRunId(runId);
        Assert.assertEquals(1, artifacts.size());
        Assert.assertEquals("req-ledger-001", artifacts.get(0).getRequestId());
        Assert.assertEquals(Long.valueOf(toolInvocationId), artifacts.get(0).getToolInvocationId());
        Assert.assertTrue(ctx.toolOutputReader.readByInvocationId("file_tool", toolInvocationId).isPresent());
    }

    @Test
    public void shouldPreserveSessionHeadForEmptyContinuationRun() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        String sessionId = "session-plan-resume-001";
        LocalDateTime now = LocalDateTime.now();

        Long sourceRunId = ctx.recorder.createRun(DialogueRunStartRecord.builder()
                .runUid("req-plan-source-001")
                .requestId("req-plan-source-001")
                .sessionId(sessionId)
                .entryAgent(ExecutionLedgerConstants.ENTRY_AGENT_PLAN_SOLVE)
                .queryText("请先制定一份项目调研计划")
                .startedAt(now)
                .build());
        ctx.recorder.finishRun(DialogueRunFinishRecord.builder()
                .runId(sourceRunId)
                .requestId("req-plan-source-001")
                .status(ExecutionLedgerConstants.STATUS_WAITING_INPUT)
                .finishedAt(now.plusSeconds(1))
                .build());

        Long resumeRunId = ctx.recorder.createRun(DialogueRunStartRecord.builder()
                .runUid("resume-plan-001")
                .requestId("resume-plan-001")
                .sessionId(sessionId)
                .entryAgent(ExecutionLedgerConstants.ENTRY_AGENT_PLAN_SOLVE)
                .queryText("")
                .startedAt(now.plusSeconds(2))
                .build());

        Assert.assertEquals("请先制定一份项目调研计划",
                ctx.sessionDao.queryBySessionId(sessionId).getTitle());
        Assert.assertEquals("请先制定一份项目调研计划",
                ctx.sessionDao.queryBySessionId(sessionId).getLatestQueryText());

        ctx.recorder.finishRun(DialogueRunFinishRecord.builder()
                .runId(resumeRunId)
                .requestId("resume-plan-001")
                .status(ExecutionLedgerConstants.STATUS_SUCCESS)
                .finalSummaryText("计划执行完成")
                .finishedAt(now.plusSeconds(3))
                .build());

        Assert.assertEquals("请先制定一份项目调研计划",
                ctx.sessionDao.queryBySessionId(sessionId).getTitle());
        Assert.assertEquals("请先制定一份项目调研计划",
                ctx.sessionDao.queryBySessionId(sessionId).getLatestQueryText());
    }

    @Test
    public void shouldRecoverLegacyDefaultSessionTitleFromRunQuery() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        String sessionId = "session-legacy-title-001";
        LocalDateTime now = LocalDateTime.now();
        Long runId = ctx.recorder.createRun(DialogueRunStartRecord.builder()
                .runUid("req-legacy-title-001")
                .requestId("req-legacy-title-001")
                .sessionId(sessionId)
                .entryAgent(ExecutionLedgerConstants.ENTRY_AGENT_PLAN_SOLVE)
                .queryText("恢复被旧续跑逻辑覆盖的标题")
                .startedAt(now)
                .build());
        ctx.recorder.finishRun(DialogueRunFinishRecord.builder()
                .runId(runId)
                .requestId("req-legacy-title-001")
                .status(ExecutionLedgerConstants.STATUS_SUCCESS)
                .finalSummaryText("完成")
                .finishedAt(now.plusSeconds(1))
                .build());

        var session = ctx.sessionDao.queryBySessionId(sessionId);
        ctx.sessionDao.upsertSession(DialogueSessionUpsertRecord.builder()
                .sessionId(sessionId)
                .visitorId(session.getVisitorId())
                .title("新对话")
                .status(session.getStatus())
                .latestRequestId(session.getLatestRequestId())
                .latestQueryText("")
                .latestSummaryText(session.getLatestSummaryText())
                .runCount(session.getRunCount())
                .finishedRunCount(session.getFinishedRunCount())
                .failedRunCount(session.getFailedRunCount())
                .startedAt(session.getStartedAt())
                .lastActiveAt(session.getLastActiveAt())
                .build());

        Assert.assertEquals("恢复被旧续跑逻辑覆盖的标题",
                ctx.queryService.querySession(sessionId).getTitle());
        Assert.assertEquals("恢复被旧续跑逻辑覆盖的标题",
                ctx.queryService.querySession(sessionId).getLatestQueryText());
        Assert.assertEquals("恢复被旧续跑逻辑覆盖的标题",
                ctx.queryService.queryRecentSessions(20).get(0).getTitle());
        Assert.assertEquals("恢复被旧续跑逻辑覆盖的标题",
                ctx.queryService.queryRecentSessions(20).get(0).getLatestQueryText());
    }

    @Test
    public void shouldKeepInputArtifactWithoutToolInvocationWhenRunFails() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        Long runId = ctx.recorder.createRun(DialogueRunStartRecord.builder()
                .runUid("req-ledger-002")
                .requestId("req-ledger-002")
                .sessionId("session-ledger-002")
                .entryAgent(ExecutionLedgerConstants.ENTRY_AGENT_PLAN_SOLVE)
                .queryText("验证失败账本")
                .startedAt(LocalDateTime.now())
                .build());

        ctx.recorder.recordArtifacts(List.of(ArtifactRecordCommand.builder()
                .runId(runId)
                .requestId("req-ledger-002")
                .artifactRole(ExecutionLedgerConstants.ARTIFACT_ROLE_INPUT)
                .visibility(ExecutionLedgerConstants.VISIBILITY_VISIBLE)
                .sourceType(ExecutionLedgerConstants.SOURCE_TYPE_USER_UPLOAD)
                .sourceName(ExecutionLedgerConstants.SOURCE_TYPE_USER_UPLOAD)
                .fileName("input.xlsx")
                .storageKey("resource://input.xlsx")
                .downloadUrl("resource://input.xlsx")
                .previewUrl("resource://input.xlsx")
                .mimeType("application/vnd.ms-excel")
                .build()));

        ctx.recorder.finishRun(DialogueRunFinishRecord.builder()
                .runId(runId)
                .requestId("req-ledger-002")
                .status(ExecutionLedgerConstants.STATUS_FAILED)
                .errorCode("PLAN_FAILED")
                .errorMsg("planner exception")
                .finishedAt(LocalDateTime.now().plusSeconds(1))
                .build());

        DialogueRun run = ctx.runDao.queryByRequestId("req-ledger-002");
        Assert.assertEquals(Integer.valueOf(ExecutionLedgerConstants.STATUS_FAILED), run.getStatus());
        Assert.assertEquals("PLAN_FAILED", run.getErrorCode());

        List<ArtifactRecord> artifacts = ctx.artifactDao.queryByRunId(runId);
        Assert.assertEquals(1, artifacts.size());
        Assert.assertEquals("req-ledger-002", artifacts.get(0).getRequestId());
        Assert.assertNull(artifacts.get(0).getToolInvocationId());
        Assert.assertEquals(ExecutionLedgerConstants.ARTIFACT_ROLE_INPUT, artifacts.get(0).getArtifactRole());
    }

    @Test
    public void shouldPersistDirectToolOutputWithoutLedgerRun() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        ctx.toolOutputWriter.write(ToolOutputPersistCommand.builder()
                .requestId("req-direct-ledger-001")
                .requestSource(ExecutionLedgerConstants.REQUEST_SOURCE_AGENT)
                .toolCallId("tool-call-direct-ledger-001")
                .toolName("report_tool")
                .status(ExecutionLedgerConstants.STATUS_FAILED)
                .errorMsg("direct timeout")
                .structuredOutput(ReportToolOutput.builder()
                        .summary("direct timeout")
                        .content("")
                        .build())
                .build());

        Assert.assertNull(ctx.runDao.queryByRequestId("req-direct-ledger-001"));
        ToolOutputView direct = ctx.toolOutputReader.readDirect("req-direct-ledger-001", "tool-call-direct-ledger-001")
                .orElseThrow();
        Assert.assertEquals("report_tool", direct.getToolName());
        Assert.assertEquals(ExecutionLedgerConstants.REQUEST_SOURCE_AGENT, direct.getRequestSource());
        Assert.assertEquals(Integer.valueOf(ExecutionLedgerConstants.STATUS_FAILED), direct.getStatus());
        Assert.assertEquals("direct timeout", direct.getErrorMsg());
    }
}
