package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.ledger.model.ArtifactRecordCommand;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunFinishRecord;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunStartRecord;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.model.LlmInvocationFinishRecord;
import org.wwz.ai.domain.agent.ledger.model.LlmInvocationStartRecord;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationBatchStartRecord;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationFinishRecord;
import org.wwz.ai.domain.agent.runtime.llm.TokenCounter;
import org.wwz.ai.infrastructure.reactor.service.impl.SessionContextMemoryServiceImpl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 单会话上下文记忆服务测试。
 */
public class SessionContextMemoryServiceTest {

    @Test
    public void shouldQueryToolInvocationsByLlmInvocationIdsInStableOrder() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        Long runId = createRun(ctx, "req-memory-order-001", "session-memory-order-001", LocalDateTime.of(2026, 5, 4, 10, 0));
        Long llmInvocationId1 = createLlmInvocation(ctx, runId, "req-memory-order-001", 1, "第一轮思考", LocalDateTime.of(2026, 5, 4, 10, 1));
        Long llmInvocationId2 = createLlmInvocation(ctx, runId, "req-memory-order-001", 2, "第二轮思考", LocalDateTime.of(2026, 5, 4, 10, 2));

        createToolInvocations(ctx, runId, "req-memory-order-001", "session-memory-order-001", llmInvocationId2,
                List.of(toolSeed("tool-call-3", 2, "file_tool", "{\"k\":3}", "obs-3", List.of())),
                LocalDateTime.of(2026, 5, 4, 10, 3));
        createToolInvocations(ctx, runId, "req-memory-order-001", "session-memory-order-001", llmInvocationId1,
                List.of(
                        toolSeed("tool-call-2", 2, "search_tool", "{\"k\":2}", "obs-2", List.of()),
                        toolSeed("tool-call-1", 1, "search_tool", "{\"k\":1}", "obs-1", List.of())
                ),
                LocalDateTime.of(2026, 5, 4, 10, 4));

        var invocations = ctx.toolDao.queryByLlmInvocationIds(List.of(llmInvocationId2, llmInvocationId1));

        Assert.assertEquals(3, invocations.size());
        Assert.assertEquals(llmInvocationId1, invocations.get(0).getLlmInvocationId());
        Assert.assertEquals(Integer.valueOf(1), invocations.get(0).getDispatchIndex());
        Assert.assertEquals("tool-call-1", invocations.get(0).getToolCallId());
        Assert.assertEquals(llmInvocationId1, invocations.get(1).getLlmInvocationId());
        Assert.assertEquals(Integer.valueOf(2), invocations.get(1).getDispatchIndex());
        Assert.assertEquals("tool-call-2", invocations.get(1).getToolCallId());
        Assert.assertEquals(llmInvocationId2, invocations.get(2).getLlmInvocationId());
        Assert.assertEquals("tool-call-3", invocations.get(2).getToolCallId());
    }

    @Test
    public void shouldBuildHistoryDialogueFromSessionLedgerFacts() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        seedHistoryRun(
                ctx,
                "req-memory-001",
                "session-memory-001",
                LocalDateTime.of(2026, 5, 4, 11, 0),
                List.of(fileSeed("brief.txt", "text/plain", 12L, "oss://brief.txt", "https://download/brief.txt", "https://preview/brief.txt")),
                List.of(cycleSeed(
                        "react",
                        1,
                        "第一轮思考",
                        List.of(toolSeed(
                                "tool-call-report-001",
                                1,
                                "file_tool",
                                "{\"query\":\"日报\"}",
                                "已生成日报",
                                List.of(fileSeed("report.md", "text/markdown", 128L, "oss://report.md", "https://download/report.md", "https://preview/report.md"))
                        ))
                ))
        );
        seedHistoryRun(
                ctx,
                "req-memory-002",
                "session-memory-001",
                LocalDateTime.of(2026, 5, 4, 11, 10),
                List.of(),
                List.of(
                        cycleSeed("react", 1, "第二轮先思考无需工具", List.of()),
                        cycleSeed(
                                "react",
                                2,
                                "第二轮继续思考后调用工具",
                                List.of(toolSeed(
                                        "tool-call-search-001",
                                        1,
                                        "deep_search_tool",
                                        "{\"topic\":\"session memory\"}",
                                        "已完成搜索",
                                        List.of()
                                ))
                        )
                )
        );
        seedHistoryRun(
                ctx,
                "req-memory-003-current",
                "session-memory-001",
                LocalDateTime.of(2026, 5, 4, 11, 20),
                List.of(),
                List.of(cycleSeed("react", 1, "当前请求不应该被注入", List.of()))
        );

        SessionContextMemoryServiceImpl service = new SessionContextMemoryServiceImpl(
                ctx.queryService,
                ctx.llmDao,
                ctx.toolDao,
                ctx.artifactDao
        );

        String historyDialogue = service.buildHistoryDialogue("session-memory-001", "req-memory-003-current");

        Assert.assertTrue(historyDialogue.startsWith("## 单会话历史记忆"));
        Assert.assertTrue(historyDialogue.contains("### Run req-memory-001"));
        Assert.assertTrue(historyDialogue.contains("[Session Input Files]"));
        Assert.assertTrue(historyDialogue.contains("fileName=brief.txt, mimeType=text/plain, fileSize=12, storageKey=oss://brief.txt, downloadUrl=https://download/brief.txt, previewUrl=https://preview/brief.txt"));
        Assert.assertTrue(historyDialogue.contains("[ReAct Cycle 1]"));
        Assert.assertTrue(historyDialogue.contains("Thought:\n第一轮思考"));
        Assert.assertTrue(historyDialogue.contains("1. toolName=file_tool"));
        Assert.assertTrue(historyDialogue.contains("toolProvider=local"));
        Assert.assertTrue(historyDialogue.contains("inputJson={\"query\":\"日报\"}"));
        Assert.assertTrue(historyDialogue.contains("llmObservation=已生成日报"));
        Assert.assertTrue(historyDialogue.contains("artifactRole=output, fileName=report.md, mimeType=text/markdown, fileSize=128, storageKey=oss://report.md, downloadUrl=https://download/report.md, previewUrl=https://preview/report.md"));
        Assert.assertTrue(historyDialogue.contains("### Run req-memory-002"));
        Assert.assertTrue(historyDialogue.contains("Thought:\n第二轮先思考无需工具"));
        Assert.assertTrue(historyDialogue.contains("Tool Calls:\n- none"));
        Assert.assertTrue(historyDialogue.contains("Thought:\n第二轮继续思考后调用工具"));
        Assert.assertTrue(historyDialogue.contains("Files:\n- none"));
        Assert.assertFalse(historyDialogue.contains("req-memory-003-current"));
        Assert.assertTrue(historyDialogue.indexOf("### Run req-memory-001") < historyDialogue.indexOf("### Run req-memory-002"));
    }

    @Test
    public void shouldReturnEmptyHistoryDialogueWhenSessionIdIsBlank() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        SessionContextMemoryServiceImpl service = new SessionContextMemoryServiceImpl(
                ctx.queryService,
                ctx.llmDao,
                ctx.toolDao,
                ctx.artifactDao
        );

        Assert.assertEquals("", service.buildHistoryDialogue("", "req-blank"));
        Assert.assertEquals("", service.buildHistoryDialogue(null, "req-blank"));
    }

    @Test
    public void shouldTruncateHistoryDialogueByMaxTokensKeepingLatestRuns() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        seedHistoryRun(
                ctx,
                "req-memory-truncate-001",
                "session-memory-truncate-001",
                LocalDateTime.of(2026, 5, 4, 12, 0),
                List.of(),
                List.of(cycleSeed("react", 1, "旧历史".repeat(40), List.of()))
        );
        seedHistoryRun(
                ctx,
                "req-memory-truncate-002",
                "session-memory-truncate-001",
                LocalDateTime.of(2026, 5, 4, 12, 10),
                List.of(),
                List.of(cycleSeed("react", 1, "中间历史".repeat(30), List.of()))
        );
        seedHistoryRun(
                ctx,
                "req-memory-truncate-003",
                "session-memory-truncate-001",
                LocalDateTime.of(2026, 5, 4, 12, 20),
                List.of(),
                List.of(cycleSeed("react", 1, "最新历史".repeat(40), List.of()))
        );
        seedHistoryRun(
                ctx,
                "req-memory-truncate-004-current",
                "session-memory-truncate-001",
                LocalDateTime.of(2026, 5, 4, 12, 30),
                List.of(),
                List.of(cycleSeed("react", 1, "当前请求不应该被注入", List.of()))
        );

        SessionContextMemoryServiceImpl service = new SessionContextMemoryServiceImpl(
                ctx.queryService,
                ctx.llmDao,
                ctx.toolDao,
                ctx.artifactDao,
                260
        );

        String historyDialogue = service.buildHistoryDialogue("session-memory-truncate-001", "req-memory-truncate-004-current");

        Assert.assertTrue(historyDialogue.startsWith("## 单会话历史记忆"));
        Assert.assertTrue(historyDialogue.contains("### Run req-memory-truncate-003"));
        Assert.assertFalse(historyDialogue.contains("### Run req-memory-truncate-001"));
        Assert.assertFalse(historyDialogue.contains("req-memory-truncate-004-current"));
        Assert.assertTrue(new TokenCounter().countText(historyDialogue) <= 260);
    }

    private Long createRun(ExecutionLedgerFixtureFactory.LedgerTestContext ctx,
                           String requestId,
                           String sessionId,
                           LocalDateTime startedAt) {
        Long runId = ctx.recorder.createRun(DialogueRunStartRecord.builder()
                .runUid(requestId)
                .requestId(requestId)
                .sessionId(sessionId)
                .entryAgent(ExecutionLedgerConstants.ENTRY_AGENT_REACT)
                .queryText("query:" + requestId)
                .startedAt(startedAt)
                .build());
        return runId;
    }

    private Long createLlmInvocation(ExecutionLedgerFixtureFactory.LedgerTestContext ctx,
                                     Long runId,
                                     String requestId,
                                     int invocationSeq,
                                     String responseText,
                                     LocalDateTime startedAt) {
        Long llmInvocationId = ctx.recorder.createLlmInvocation(LlmInvocationStartRecord.builder()
                .runId(runId)
                .requestId(requestId)
                .invocationSeq(invocationSeq)
                .agentName("react")
                .stepNo(invocationSeq)
                .callKind(ExecutionLedgerConstants.CALL_KIND_ASK_TOOL)
                .streaming(false)
                .modelName("test-model")
                .startedAt(startedAt)
                .build());
        ctx.recorder.finishLlmInvocation(LlmInvocationFinishRecord.builder()
                .llmInvocationId(llmInvocationId)
                .requestId(requestId)
                .status(ExecutionLedgerConstants.STATUS_SUCCESS)
                .responseText(responseText)
                .toolCallCount(0)
                .promptTokens(10)
                .completionTokens(20)
                .totalTokens(30)
                .finishReason("stop")
                .finishedAt(startedAt.plusSeconds(1))
                .build());
        return llmInvocationId;
    }

    private void seedHistoryRun(ExecutionLedgerFixtureFactory.LedgerTestContext ctx,
                                String requestId,
                                String sessionId,
                                LocalDateTime startedAt,
                                List<FileSeed> inputFiles,
                                List<CycleSeed> cycles) {
        Long runId = createRun(ctx, requestId, sessionId, startedAt);
        recordInputFiles(ctx, runId, requestId, inputFiles);
        int cycleIndex = 0;
        for (CycleSeed cycle : cycles) {
            cycleIndex += 1;
            Long llmInvocationId = createLlmInvocation(
                    ctx,
                    runId,
                    requestId,
                    cycleIndex,
                    cycle.thoughtContent,
                    startedAt.plusMinutes(cycleIndex)
            );
            createToolInvocations(
                    ctx,
                    runId,
                    requestId,
                    sessionId,
                    llmInvocationId,
                    cycle.tools,
                    startedAt.plusMinutes(cycleIndex).plusSeconds(10)
            );
        }
        ctx.recorder.finishRun(DialogueRunFinishRecord.builder()
                .runId(runId)
                .requestId(requestId)
                .status(ExecutionLedgerConstants.STATUS_SUCCESS)
                .finalSummaryText("summary:" + requestId)
                .finishedAt(startedAt.plusMinutes(cycles.size() + 1L))
                .build());
    }

    private void recordInputFiles(ExecutionLedgerFixtureFactory.LedgerTestContext ctx,
                                  Long runId,
                                  String requestId,
                                  List<FileSeed> inputFiles) {
        if (inputFiles == null || inputFiles.isEmpty()) {
            return;
        }
        List<ArtifactRecordCommand> records = new ArrayList<>();
        for (FileSeed file : inputFiles) {
            records.add(ArtifactRecordCommand.builder()
                    .runId(runId)
                    .requestId(requestId)
                    .artifactRole(ExecutionLedgerConstants.ARTIFACT_ROLE_INPUT)
                    .visibility(ExecutionLedgerConstants.VISIBILITY_VISIBLE)
                    .sourceType(ExecutionLedgerConstants.SOURCE_TYPE_USER_UPLOAD)
                    .sourceName(ExecutionLedgerConstants.SOURCE_TYPE_USER_UPLOAD)
                    .fileName(file.fileName)
                    .storageKey(file.storageKey)
                    .downloadUrl(file.downloadUrl)
                    .previewUrl(file.previewUrl)
                    .mimeType(file.mimeType)
                    .fileSize(file.fileSize)
                    .build());
        }
        ctx.recorder.recordArtifacts(records);
    }

    private void createToolInvocations(ExecutionLedgerFixtureFactory.LedgerTestContext ctx,
                                       Long runId,
                                       String requestId,
                                       String sessionId,
                                       Long llmInvocationId,
                                       List<ToolSeed> toolSeeds,
                                       LocalDateTime startedAt) {
        if (toolSeeds == null || toolSeeds.isEmpty()) {
            return;
        }
        List<ToolInvocationBatchStartRecord.Item> items = new ArrayList<>();
        for (ToolSeed toolSeed : toolSeeds) {
            items.add(ToolInvocationBatchStartRecord.Item.builder()
                    .toolCallId(toolSeed.toolCallId)
                    .dispatchIndex(toolSeed.dispatchIndex)
                    .toolName(toolSeed.toolName)
                    .toolProvider(ExecutionLedgerConstants.TOOL_PROVIDER_LOCAL)
                    .inputJson(toolSeed.inputJson)
                    .startedAt(startedAt.plusSeconds(toolSeed.dispatchIndex))
                    .build());
        }
        Map<String, Long> toolInvocationIds = ctx.recorder.createToolInvocations(ToolInvocationBatchStartRecord.builder()
                .runId(runId)
                .requestId(requestId)
                .llmInvocationId(llmInvocationId)
                .agentName("react")
                .stepNo(1)
                .items(items)
                .build());
        for (ToolSeed toolSeed : toolSeeds) {
            Long toolInvocationId = toolInvocationIds.get(toolSeed.toolCallId);
            ctx.recorder.finishToolInvocation(ToolInvocationFinishRecord.builder()
                    .toolInvocationId(toolInvocationId)
                    .runId(runId)
                    .requestId(requestId)
                    .sessionId(sessionId)
                    .toolCallId(toolSeed.toolCallId)
                    .toolName(toolSeed.toolName)
                    .status(ExecutionLedgerConstants.STATUS_SUCCESS)
                    .llmObservation(toolSeed.llmObservation)
                    .finishedAt(startedAt.plusSeconds(20L + toolSeed.dispatchIndex))
                    .build());
            if (toolSeed.files.isEmpty()) {
                continue;
            }
            List<ArtifactRecordCommand> artifactRecords = new ArrayList<>();
            for (FileSeed file : toolSeed.files) {
                artifactRecords.add(ArtifactRecordCommand.builder()
                        .runId(runId)
                        .requestId(requestId)
                        .toolInvocationId(toolInvocationId)
                        .toolCallId(toolSeed.toolCallId)
                        .artifactRole(ExecutionLedgerConstants.ARTIFACT_ROLE_OUTPUT)
                        .visibility(ExecutionLedgerConstants.VISIBILITY_VISIBLE)
                        .sourceType(ExecutionLedgerConstants.SOURCE_TYPE_TOOL_OUTPUT)
                        .sourceName(toolSeed.toolName)
                        .fileName(file.fileName)
                        .storageKey(file.storageKey)
                        .downloadUrl(file.downloadUrl)
                        .previewUrl(file.previewUrl)
                        .mimeType(file.mimeType)
                        .fileSize(file.fileSize)
                        .build());
            }
            ctx.recorder.recordArtifacts(artifactRecords);
        }
    }

    private CycleSeed cycleSeed(String agentName, int invocationSeq, String thoughtContent, List<ToolSeed> tools) {
        return new CycleSeed(agentName, invocationSeq, thoughtContent, tools);
    }

    private ToolSeed toolSeed(String toolCallId,
                              int dispatchIndex,
                              String toolName,
                              String inputJson,
                              String llmObservation,
                              List<FileSeed> files) {
        return new ToolSeed(toolCallId, dispatchIndex, toolName, inputJson, llmObservation, files);
    }

    private FileSeed fileSeed(String fileName,
                              String mimeType,
                              Long fileSize,
                              String storageKey,
                              String downloadUrl,
                              String previewUrl) {
        return new FileSeed(fileName, mimeType, fileSize, storageKey, downloadUrl, previewUrl);
    }

    private record CycleSeed(String agentName, int invocationSeq, String thoughtContent, List<ToolSeed> tools) {
    }

    private record ToolSeed(String toolCallId,
                            int dispatchIndex,
                            String toolName,
                            String inputJson,
                            String llmObservation,
                            List<FileSeed> files) {
    }

    private record FileSeed(String fileName,
                            String mimeType,
                            Long fileSize,
                            String storageKey,
                            String downloadUrl,
                            String previewUrl) {
    }
}
