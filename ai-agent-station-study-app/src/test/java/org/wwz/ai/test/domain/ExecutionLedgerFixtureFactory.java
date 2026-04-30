package org.wwz.ai.test.domain;

import org.wwz.ai.domain.agent.reactor.agent.agent.AgentContext;
import org.wwz.ai.domain.agent.reactor.agent.dto.File;
import org.wwz.ai.domain.agent.reactor.agent.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.reactor.agent.tool.ToolCollection;
import org.wwz.ai.domain.agent.reactor.entity.ArtifactRecord;
import org.wwz.ai.domain.agent.reactor.entity.DialogueRun;
import org.wwz.ai.domain.agent.reactor.entity.LlmInvocation;
import org.wwz.ai.domain.agent.reactor.entity.ToolInvocation;
import org.wwz.ai.domain.agent.reactor.mapper.IArtifactLedgerDao;
import org.wwz.ai.domain.agent.reactor.mapper.IDialogueRunLedgerDao;
import org.wwz.ai.domain.agent.reactor.mapper.ILlmInvocationLedgerDao;
import org.wwz.ai.domain.agent.reactor.mapper.IToolInvocationLedgerDao;
import org.wwz.ai.domain.agent.reactor.model.ledger.DialogueRunView;
import org.wwz.ai.domain.agent.reactor.model.ledger.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.reactor.model.ledger.LlmInvocationStartRecord;
import org.wwz.ai.domain.agent.reactor.model.ledger.ToolInvocationView;
import org.wwz.ai.domain.agent.reactor.service.AgentExecutionRecorder;
import org.wwz.ai.domain.agent.reactor.service.ExecutionLedgerQueryService;
import org.wwz.ai.domain.agent.reactor.service.impl.AgentExecutionRecorderImpl;
import org.wwz.ai.domain.agent.reactor.service.impl.ExecutionLedgerQueryServiceImpl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 执行账本测试夹具。
 * 使用内存 DAO 替代真实 MyBatis/数据库，便于在 application-test 禁库配置下做纯单元回归。
 */
public final class ExecutionLedgerFixtureFactory {

    private ExecutionLedgerFixtureFactory() {
    }

    static LedgerTestContext newLedgerTestContext() {
        InMemoryLedgerStore store = new InMemoryLedgerStore();
        InMemoryDialogueRunLedgerDao runDao = new InMemoryDialogueRunLedgerDao(store);
        InMemoryLlmInvocationLedgerDao llmDao = new InMemoryLlmInvocationLedgerDao(store);
        InMemoryToolInvocationLedgerDao toolDao = new InMemoryToolInvocationLedgerDao(store);
        InMemoryArtifactLedgerDao artifactDao = new InMemoryArtifactLedgerDao(store);
        AgentExecutionRecorder recorder = new AgentExecutionRecorderImpl(runDao, llmDao, toolDao, artifactDao);
        ExecutionLedgerQueryService queryService = new ExecutionLedgerQueryServiceImpl(runDao, llmDao, toolDao, artifactDao);
        return new LedgerTestContext(store, recorder, queryService, runDao, llmDao, toolDao, artifactDao);
    }

    static AgentContext newAgentContext(String requestId, String sessionId, AgentExecutionRecorder recorder) {
        ToolCollection toolCollection = new ToolCollection();
        AgentContext context = AgentContext.builder()
                .requestId(requestId)
                .sessionId(sessionId)
                .query("测试执行账本")
                .toolCollection(toolCollection)
                .productFiles(new ArrayList<>())
                .taskProductFiles(new ArrayList<>())
                .executionRecorder(recorder)
                .build();
        toolCollection.setAgentContext(context);
        return context;
    }

    static Long activateRun(AgentContext context, AgentExecutionRecorder recorder, String entryAgent) {
        Long runId = recorder.createRun(org.wwz.ai.domain.agent.reactor.model.ledger.DialogueRunStartRecord.builder()
                .runUid(context.getRequestId())
                .requestId(context.getRequestId())
                .sessionId(context.getSessionId())
                .entryAgent(entryAgent)
                .queryText(context.getQuery())
                .startedAt(LocalDateTime.now())
                .build());
        context.activateLedgerRun(runId, context.getRequestId());
        return runId;
    }

    static Long createLlmInvocation(AgentContext context,
                                    AgentExecutionRecorder recorder,
                                    String agentName,
                                    Integer stepNo,
                                    String callKind) {
        context.markExecutionPosition(agentName, stepNo);
        Long llmInvocationId = recorder.createLlmInvocation(LlmInvocationStartRecord.builder()
                .runId(context.getAgentRunState().getRunId())
                .requestId(context.getRequestId())
                .invocationSeq(context.getAgentRunState().nextInvocationSeq())
                .agentName(agentName)
                .stepNo(stepNo)
                .callKind(callKind)
                .streaming(false)
                .modelName("test-model")
                .startedAt(LocalDateTime.now())
                .build());
        context.getAgentRunState().bindCurrentLlmInvocationId(llmInvocationId);
        return llmInvocationId;
    }

    static ToolCall newToolCall(String id, String toolName, String arguments) {
        return ToolCall.builder()
                .id(id)
                .type("function")
                .function(ToolCall.Function.builder()
                        .name(toolName)
                        .arguments(arguments)
                        .build())
                .build();
    }

    static File newFile(String fileName, String url, boolean internalFile) {
        return File.builder()
                .fileName(fileName)
                .ossUrl(url)
                .domainUrl(url)
                .isInternalFile(internalFile)
                .build();
    }

    static final class LedgerTestContext {
        final InMemoryLedgerStore store;
        final AgentExecutionRecorder recorder;
        final ExecutionLedgerQueryService queryService;
        final IDialogueRunLedgerDao runDao;
        final ILlmInvocationLedgerDao llmDao;
        final IToolInvocationLedgerDao toolDao;
        final IArtifactLedgerDao artifactDao;

        private LedgerTestContext(InMemoryLedgerStore store,
                                  AgentExecutionRecorder recorder,
                                  ExecutionLedgerQueryService queryService,
                                  IDialogueRunLedgerDao runDao,
                                  ILlmInvocationLedgerDao llmDao,
                                  IToolInvocationLedgerDao toolDao,
                                  IArtifactLedgerDao artifactDao) {
            this.store = store;
            this.recorder = recorder;
            this.queryService = queryService;
            this.runDao = runDao;
            this.llmDao = llmDao;
            this.toolDao = toolDao;
            this.artifactDao = artifactDao;
        }
    }

    /**
     * 共享内存账本存储。
     */
    static final class InMemoryLedgerStore {
        long nextRunId = 1L;
        long nextLlmId = 1L;
        long nextToolId = 1L;
        long nextArtifactId = 1L;
        Map<Long, DialogueRun> runs = new LinkedHashMap<>();
        Map<Long, LlmInvocation> llmInvocations = new LinkedHashMap<>();
        Map<Long, ToolInvocation> toolInvocations = new LinkedHashMap<>();
        Map<Long, ArtifactRecord> artifacts = new LinkedHashMap<>();
    }

    static final class InMemoryDialogueRunLedgerDao implements IDialogueRunLedgerDao {
        private final InMemoryLedgerStore store;

        private InMemoryDialogueRunLedgerDao(InMemoryLedgerStore store) {
            this.store = store;
        }

        @Override
        public int insertRun(DialogueRun run) {
            for (DialogueRun existing : store.runs.values()) {
                if (existing.getDeleted() == 0
                        && (existing.getRequestId().equals(run.getRequestId()) || existing.getRunUid().equals(run.getRunUid()))) {
                    throw new IllegalStateException("duplicate run key");
                }
            }
            run.setId(store.nextRunId++);
            run.setCreateTime(LocalDateTime.now());
            run.setUpdateTime(run.getCreateTime());
            run.setDeleted(0);
            store.runs.put(run.getId(), cloneRun(run));
            return 1;
        }

        @Override
        public int updateRunFinish(DialogueRun run) {
            DialogueRun existing = store.runs.get(run.getId());
            if (existing == null) {
                return 0;
            }
            existing.setStatus(run.getStatus());
            existing.setFinalSummaryText(run.getFinalSummaryText());
            existing.setLlmCallCount(run.getLlmCallCount());
            existing.setToolCallCount(run.getToolCallCount());
            existing.setArtifactCount(run.getArtifactCount());
            existing.setPromptTokensTotal(run.getPromptTokensTotal());
            existing.setCompletionTokensTotal(run.getCompletionTokensTotal());
            existing.setTotalTokensTotal(run.getTotalTokensTotal());
            existing.setErrorCode(run.getErrorCode());
            existing.setErrorMsg(run.getErrorMsg());
            existing.setFinishedAt(run.getFinishedAt());
            existing.setDurationMs(run.getDurationMs());
            existing.setUpdateTime(LocalDateTime.now());
            return 1;
        }

        @Override
        public DialogueRun queryByRequestId(String requestId) {
            return store.runs.values().stream()
                    .filter(item -> item.getDeleted() == 0 && item.getRequestId().equals(requestId))
                    .findFirst()
                    .map(ExecutionLedgerFixtureFactory::cloneRun)
                    .orElse(null);
        }

        @Override
        public List<DialogueRunView> queryRecentBySessionId(String sessionId, int limit) {
            return store.runs.values().stream()
                    .filter(item -> item.getDeleted() == 0 && item.getSessionId().equals(sessionId))
                    .sorted(Comparator.comparing(DialogueRun::getCreateTime, Comparator.reverseOrder())
                            .thenComparing(DialogueRun::getId, Comparator.reverseOrder()))
                    .limit(limit)
                    .map(ExecutionLedgerFixtureFactory::toRunView)
                    .toList();
        }
    }

    static final class InMemoryLlmInvocationLedgerDao implements ILlmInvocationLedgerDao {
        private final InMemoryLedgerStore store;

        private InMemoryLlmInvocationLedgerDao(InMemoryLedgerStore store) {
            this.store = store;
        }

        @Override
        public int insertLlmInvocation(LlmInvocation invocation) {
            for (LlmInvocation existing : store.llmInvocations.values()) {
                if (existing.getDeleted() == 0
                        && existing.getRunId().equals(invocation.getRunId())
                        && existing.getInvocationSeq().equals(invocation.getInvocationSeq())) {
                    throw new IllegalStateException("duplicate llm invocation seq");
                }
            }
            invocation.setId(store.nextLlmId++);
            invocation.setCreateTime(LocalDateTime.now());
            invocation.setUpdateTime(invocation.getCreateTime());
            invocation.setDeleted(0);
            store.llmInvocations.put(invocation.getId(), cloneLlm(invocation));
            return 1;
        }

        @Override
        public int updateLlmInvocationFinish(LlmInvocation invocation) {
            LlmInvocation existing = store.llmInvocations.get(invocation.getId());
            if (existing == null) {
                return 0;
            }
            existing.setStatus(invocation.getStatus());
            existing.setResponseText(invocation.getResponseText());
            existing.setToolCallCount(invocation.getToolCallCount());
            existing.setPromptTokens(invocation.getPromptTokens());
            existing.setCompletionTokens(invocation.getCompletionTokens());
            existing.setTotalTokens(invocation.getTotalTokens());
            existing.setFinishReason(invocation.getFinishReason());
            existing.setErrorMsg(invocation.getErrorMsg());
            existing.setFinishedAt(invocation.getFinishedAt());
            existing.setDurationMs(duration(existing.getStartedAt(), invocation.getFinishedAt()));
            existing.setUpdateTime(LocalDateTime.now());
            return 1;
        }

        @Override
        public List<LlmInvocation> queryByRunId(Long runId) {
            return store.llmInvocations.values().stream()
                    .filter(item -> item.getDeleted() == 0 && item.getRunId().equals(runId))
                    .sorted(Comparator.comparing(LlmInvocation::getInvocationSeq).thenComparing(LlmInvocation::getId))
                    .map(ExecutionLedgerFixtureFactory::cloneLlm)
                    .toList();
        }
    }

    static final class InMemoryToolInvocationLedgerDao implements IToolInvocationLedgerDao {
        private final InMemoryLedgerStore store;

        private InMemoryToolInvocationLedgerDao(InMemoryLedgerStore store) {
            this.store = store;
        }

        @Override
        public int insertToolInvocation(ToolInvocation invocation) {
            for (ToolInvocation existing : store.toolInvocations.values()) {
                if (existing.getDeleted() != 0) {
                    continue;
                }
                if (existing.getRunId().equals(invocation.getRunId())
                        && existing.getToolCallId().equals(invocation.getToolCallId())) {
                    throw new IllegalStateException("duplicate run/toolCallId");
                }
                if (existing.getLlmInvocationId().equals(invocation.getLlmInvocationId())
                        && existing.getDispatchIndex().equals(invocation.getDispatchIndex())) {
                    throw new IllegalStateException("duplicate llm/dispatchIndex");
                }
            }
            invocation.setId(store.nextToolId++);
            invocation.setCreateTime(LocalDateTime.now());
            invocation.setUpdateTime(invocation.getCreateTime());
            invocation.setDeleted(0);
            store.toolInvocations.put(invocation.getId(), cloneTool(invocation));
            return 1;
        }

        @Override
        public int updateToolInvocationFinish(ToolInvocation invocation) {
            ToolInvocation existing = store.toolInvocations.get(invocation.getId());
            if (existing == null) {
                return 0;
            }
            existing.setStatus(invocation.getStatus());
            existing.setOutputText(invocation.getOutputText());
            existing.setOutputJson(invocation.getOutputJson());
            existing.setErrorMsg(invocation.getErrorMsg());
            existing.setFinishedAt(invocation.getFinishedAt());
            existing.setDurationMs(duration(existing.getStartedAt(), invocation.getFinishedAt()));
            existing.setUpdateTime(LocalDateTime.now());
            return 1;
        }

        @Override
        public List<ToolInvocation> queryByRunId(Long runId) {
            return store.toolInvocations.values().stream()
                    .filter(item -> item.getDeleted() == 0 && item.getRunId().equals(runId))
                    .sorted(Comparator.comparing(ToolInvocation::getLlmInvocationId)
                            .thenComparing(ToolInvocation::getDispatchIndex)
                            .thenComparing(ToolInvocation::getId))
                    .map(ExecutionLedgerFixtureFactory::cloneTool)
                    .toList();
        }

        @Override
        public List<ToolInvocationView> queryRecentByToolName(String toolName, int limit) {
            return store.toolInvocations.values().stream()
                    .filter(item -> item.getDeleted() == 0 && item.getToolName().equals(toolName))
                    .sorted(Comparator.comparing(ToolInvocation::getCreateTime, Comparator.reverseOrder())
                            .thenComparing(ToolInvocation::getId, Comparator.reverseOrder()))
                    .limit(limit)
                    .map(item -> {
                        DialogueRun run = store.runs.get(item.getRunId());
                        int artifactCount = (int) store.artifacts.values().stream()
                                .filter(artifact -> artifact.getDeleted() == 0 && item.getId().equals(artifact.getToolInvocationId()))
                                .count();
                        return ToolInvocationView.builder()
                                .id(item.getId())
                                .runId(item.getRunId())
                                .llmInvocationId(item.getLlmInvocationId())
                                .requestId(run == null ? null : run.getRequestId())
                                .sessionId(run == null ? null : run.getSessionId())
                                .toolCallId(item.getToolCallId())
                                .dispatchIndex(item.getDispatchIndex())
                                .agentName(item.getAgentName())
                                .stepNo(item.getStepNo())
                                .toolName(item.getToolName())
                                .toolProvider(item.getToolProvider())
                                .inputJson(item.getInputJson())
                                .outputText(item.getOutputText())
                                .outputJson(item.getOutputJson())
                                .status(item.getStatus())
                                .errorMsg(item.getErrorMsg())
                                .durationMs(item.getDurationMs())
                                .artifactCount(artifactCount)
                                .startedAt(item.getStartedAt())
                                .finishedAt(item.getFinishedAt())
                                .createTime(item.getCreateTime())
                                .build();
                    })
                    .toList();
        }
    }

    static final class InMemoryArtifactLedgerDao implements IArtifactLedgerDao {
        private final InMemoryLedgerStore store;

        private InMemoryArtifactLedgerDao(InMemoryLedgerStore store) {
            this.store = store;
        }

        @Override
        public int batchInsertArtifacts(List<ArtifactRecord> records) {
            int inserted = 0;
            for (ArtifactRecord record : records) {
                boolean exists = store.artifacts.values().stream()
                        .anyMatch(existing -> existing.getDeleted() == 0
                                && existing.getRunId().equals(record.getRunId())
                                && equalsNullable(existing.getToolCallId(), record.getToolCallId())
                                && equalsNullable(existing.getStorageKey(), record.getStorageKey()));
                if (exists) {
                    continue;
                }
                record.setId(store.nextArtifactId++);
                record.setCreateTime(LocalDateTime.now());
                record.setUpdateTime(record.getCreateTime());
                record.setDeleted(0);
                store.artifacts.put(record.getId(), cloneArtifact(record));
                inserted++;
            }
            return inserted;
        }

        @Override
        public List<ArtifactRecord> queryByRunId(Long runId) {
            return store.artifacts.values().stream()
                    .filter(item -> item.getDeleted() == 0 && item.getRunId().equals(runId))
                    .sorted(Comparator.comparing(ArtifactRecord::getCreateTime).thenComparing(ArtifactRecord::getId))
                    .map(ExecutionLedgerFixtureFactory::cloneArtifact)
                    .toList();
        }

        @Override
        public List<ArtifactRecord> queryByRunIds(List<Long> runIds) {
            return store.artifacts.values().stream()
                    .filter(item -> item.getDeleted() == 0 && runIds.contains(item.getRunId()))
                    .sorted(Comparator.comparing(ArtifactRecord::getRunId).reversed()
                            .thenComparing(ArtifactRecord::getCreateTime)
                            .thenComparing(ArtifactRecord::getId))
                    .map(ExecutionLedgerFixtureFactory::cloneArtifact)
                    .toList();
        }
    }

    private static boolean equalsNullable(Object left, Object right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }

    private static long duration(LocalDateTime startedAt, LocalDateTime finishedAt) {
        if (startedAt == null || finishedAt == null) {
            return 0L;
        }
        return Duration.between(startedAt, finishedAt).toMillis();
    }

    private static DialogueRun cloneRun(DialogueRun run) {
        return DialogueRun.builder()
                .id(run.getId())
                .runUid(run.getRunUid())
                .requestId(run.getRequestId())
                .sessionId(run.getSessionId())
                .entryAgent(run.getEntryAgent())
                .status(run.getStatus())
                .queryText(run.getQueryText())
                .finalSummaryText(run.getFinalSummaryText())
                .llmCallCount(run.getLlmCallCount())
                .toolCallCount(run.getToolCallCount())
                .artifactCount(run.getArtifactCount())
                .promptTokensTotal(run.getPromptTokensTotal())
                .completionTokensTotal(run.getCompletionTokensTotal())
                .totalTokensTotal(run.getTotalTokensTotal())
                .errorCode(run.getErrorCode())
                .errorMsg(run.getErrorMsg())
                .startedAt(run.getStartedAt())
                .finishedAt(run.getFinishedAt())
                .durationMs(run.getDurationMs())
                .createTime(run.getCreateTime())
                .updateTime(run.getUpdateTime())
                .deleted(run.getDeleted())
                .build();
    }

    private static LlmInvocation cloneLlm(LlmInvocation invocation) {
        return LlmInvocation.builder()
                .id(invocation.getId())
                .runId(invocation.getRunId())
                .invocationSeq(invocation.getInvocationSeq())
                .agentName(invocation.getAgentName())
                .stepNo(invocation.getStepNo())
                .callKind(invocation.getCallKind())
                .streaming(invocation.getStreaming())
                .modelName(invocation.getModelName())
                .responseText(invocation.getResponseText())
                .toolCallCount(invocation.getToolCallCount())
                .promptTokens(invocation.getPromptTokens())
                .completionTokens(invocation.getCompletionTokens())
                .totalTokens(invocation.getTotalTokens())
                .finishReason(invocation.getFinishReason())
                .status(invocation.getStatus())
                .errorMsg(invocation.getErrorMsg())
                .startedAt(invocation.getStartedAt())
                .finishedAt(invocation.getFinishedAt())
                .durationMs(invocation.getDurationMs())
                .createTime(invocation.getCreateTime())
                .updateTime(invocation.getUpdateTime())
                .deleted(invocation.getDeleted())
                .build();
    }

    private static ToolInvocation cloneTool(ToolInvocation invocation) {
        return ToolInvocation.builder()
                .id(invocation.getId())
                .runId(invocation.getRunId())
                .llmInvocationId(invocation.getLlmInvocationId())
                .toolCallId(invocation.getToolCallId())
                .dispatchIndex(invocation.getDispatchIndex())
                .agentName(invocation.getAgentName())
                .stepNo(invocation.getStepNo())
                .toolName(invocation.getToolName())
                .toolProvider(invocation.getToolProvider())
                .inputJson(invocation.getInputJson())
                .outputText(invocation.getOutputText())
                .outputJson(invocation.getOutputJson())
                .status(invocation.getStatus())
                .errorMsg(invocation.getErrorMsg())
                .startedAt(invocation.getStartedAt())
                .finishedAt(invocation.getFinishedAt())
                .durationMs(invocation.getDurationMs())
                .createTime(invocation.getCreateTime())
                .updateTime(invocation.getUpdateTime())
                .deleted(invocation.getDeleted())
                .build();
    }

    private static ArtifactRecord cloneArtifact(ArtifactRecord artifact) {
        return ArtifactRecord.builder()
                .id(artifact.getId())
                .runId(artifact.getRunId())
                .toolInvocationId(artifact.getToolInvocationId())
                .toolCallId(artifact.getToolCallId())
                .artifactRole(artifact.getArtifactRole())
                .visibility(artifact.getVisibility())
                .sourceType(artifact.getSourceType())
                .sourceName(artifact.getSourceName())
                .fileName(artifact.getFileName())
                .storageKey(artifact.getStorageKey())
                .downloadUrl(artifact.getDownloadUrl())
                .previewUrl(artifact.getPreviewUrl())
                .mimeType(artifact.getMimeType())
                .fileSize(artifact.getFileSize())
                .fileHash(artifact.getFileHash())
                .metadataJson(artifact.getMetadataJson())
                .createTime(artifact.getCreateTime())
                .updateTime(artifact.getUpdateTime())
                .deleted(artifact.getDeleted())
                .build();
    }

    private static DialogueRunView toRunView(DialogueRun run) {
        return DialogueRunView.builder()
                .id(run.getId())
                .runUid(run.getRunUid())
                .requestId(run.getRequestId())
                .sessionId(run.getSessionId())
                .entryAgent(run.getEntryAgent())
                .status(run.getStatus())
                .queryText(run.getQueryText())
                .finalSummaryText(run.getFinalSummaryText())
                .llmCallCount(run.getLlmCallCount())
                .toolCallCount(run.getToolCallCount())
                .artifactCount(run.getArtifactCount())
                .promptTokensTotal(run.getPromptTokensTotal())
                .completionTokensTotal(run.getCompletionTokensTotal())
                .totalTokensTotal(run.getTotalTokensTotal())
                .errorCode(run.getErrorCode())
                .errorMsg(run.getErrorMsg())
                .startedAt(run.getStartedAt())
                .finishedAt(run.getFinishedAt())
                .durationMs(run.getDurationMs())
                .createTime(run.getCreateTime())
                .build();
    }
}
