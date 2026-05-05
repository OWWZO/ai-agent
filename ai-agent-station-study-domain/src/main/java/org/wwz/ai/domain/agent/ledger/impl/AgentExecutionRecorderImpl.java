package org.wwz.ai.domain.agent.ledger.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.ledger.IExecutionLedgerWriteRepository;
import org.wwz.ai.domain.agent.ledger.entity.ArtifactRecord;
import org.wwz.ai.domain.agent.ledger.entity.DialogueRun;
import org.wwz.ai.domain.agent.ledger.entity.LlmInvocation;
import org.wwz.ai.domain.agent.ledger.entity.ToolInvocation;
import org.wwz.ai.domain.agent.ledger.model.ArtifactRecordCommand;
import org.wwz.ai.domain.agent.ledger.model.DialogueSessionUpsertRecord;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunFinishRecord;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunStartRecord;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunView;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.model.LlmInvocationFinishRecord;
import org.wwz.ai.domain.agent.ledger.model.LlmInvocationStartRecord;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationBatchStartRecord;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationFinishRecord;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolOutputNames;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolOutputPersistCommand;
import org.wwz.ai.domain.agent.ledger.AgentExecutionRecorder;
import org.wwz.ai.domain.agent.ledger.tooloutput.ToolOutputWriter;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * 执行账本写入服务。
 * 统一承担 fail-open、日志与内存指标累计，避免调用点到处散落 try/catch。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentExecutionRecorderImpl implements AgentExecutionRecorder {

    private final IExecutionLedgerWriteRepository executionLedgerWriteRepository;
    private final ToolOutputWriter toolOutputWriter;

    private final Map<String, LongAdder> successCounters = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> failureCounters = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> durationTotals = new ConcurrentHashMap<>();

    @Override
    public Long createRun(DialogueRunStartRecord record) {
        if (record == null || StringUtils.isBlank(record.getRequestId())) {
            return null;
        }
        LocalDateTime startedAt = defaultNow(record.getStartedAt());
        DialogueRun entity = DialogueRun.builder()
                .runUid(StringUtils.defaultIfBlank(record.getRunUid(), record.getRequestId()))
                .requestId(record.getRequestId())
                .sessionId(record.getSessionId())
                .visitorId(record.getVisitorId())
                .entryAgent(record.getEntryAgent())
                .status(ExecutionLedgerConstants.STATUS_RUNNING)
                .queryText(record.getQueryText())
                .llmCallCount(0)
                .toolCallCount(0)
                .artifactCount(0)
                .promptTokensTotal(0)
                .completionTokensTotal(0)
                .totalTokensTotal(0)
                .startedAt(startedAt)
                .build();
        try {
            executionLedgerWriteRepository.insertRun(entity);
            upsertSessionHead(DialogueSessionUpsertRecord.builder()
                    .sessionId(record.getSessionId())
                    .visitorId(record.getVisitorId())
                    .title(resolveSessionTitle(record.getQueryText()))
                    .status(ExecutionLedgerConstants.STATUS_RUNNING)
                    .latestRequestId(record.getRequestId())
                    .latestQueryText(record.getQueryText())
                    .latestSummaryText(null)
                    .runCount(increaseSessionRunCount(record.getSessionId()))
                    .finishedRunCount(queryFinishedRunCount(record.getSessionId()))
                    .failedRunCount(queryFailedRunCount(record.getSessionId()))
                    .startedAt(resolveSessionStartedAt(record.getSessionId(), startedAt))
                    .lastActiveAt(startedAt)
                    .build());
            markSuccess("createRun", null);
            return entity.getId();
        } catch (Exception e) {
            markFailure("createRun", record.getRequestId(), null, null, e);
            return null;
        }
    }

    @Override
    public void finishRun(DialogueRunFinishRecord record) {
        if (record == null || record.getRunId() == null || StringUtils.isBlank(record.getRequestId())) {
            return;
        }
        try {
            DialogueRun existing = executionLedgerWriteRepository.queryRunByRequestId(record.getRequestId());
            if (existing == null) {
                return;
            }
            List<LlmInvocation> llmInvocations = executionLedgerWriteRepository.queryLlmInvocationsByRunId(existing.getId());
            List<ToolInvocation> toolInvocations = executionLedgerWriteRepository.queryToolInvocationsByRunId(existing.getId());
            List<ArtifactRecord> artifacts = executionLedgerWriteRepository.queryArtifactsByRunId(existing.getId());
            LocalDateTime finishedAt = defaultNow(record.getFinishedAt());
            DialogueRun updateEntity = DialogueRun.builder()
                    .id(existing.getId())
                    .status(record.getStatus())
                    .finalSummaryText(record.getFinalSummaryText())
                    .llmCallCount(sizeOf(llmInvocations))
                    .toolCallCount(sizeOf(toolInvocations))
                    .artifactCount(sizeOf(artifacts))
                    .promptTokensTotal(sumPromptTokens(llmInvocations))
                    .completionTokensTotal(sumCompletionTokens(llmInvocations))
                    .totalTokensTotal(sumTotalTokens(llmInvocations))
                    .errorCode(record.getErrorCode())
                    .errorMsg(trimText(record.getErrorMsg(), 2000))
                    .finishedAt(finishedAt)
                    .durationMs(calculateDuration(existing.getStartedAt(), finishedAt))
                    .build();
            executionLedgerWriteRepository.updateRunFinish(updateEntity);
            upsertSessionHead(DialogueSessionUpsertRecord.builder()
                    .sessionId(existing.getSessionId())
                    .visitorId(existing.getVisitorId())
                    .title(resolveSessionTitle(existing.getQueryText()))
                    .status(record.getStatus())
                    .latestRequestId(existing.getRequestId())
                    .latestQueryText(existing.getQueryText())
                    .latestSummaryText(record.getFinalSummaryText())
                    .runCount(queryRunCount(existing.getSessionId()))
                    .finishedRunCount(queryFinishedRunCount(existing.getSessionId()))
                    .failedRunCount(queryFailedRunCount(existing.getSessionId()))
                    .startedAt(resolveSessionStartedAt(existing.getSessionId(), existing.getStartedAt()))
                    .lastActiveAt(finishedAt)
                    .build());
            markSuccess("finishRun", updateEntity.getDurationMs());
        } catch (Exception e) {
            markFailure("finishRun", record.getRequestId(), record.getRunId(), null, e);
        }
    }

    @Override
    public Long createLlmInvocation(LlmInvocationStartRecord record) {
        if (record == null || record.getRunId() == null) {
            return null;
        }
        LlmInvocation entity = LlmInvocation.builder()
                .runId(record.getRunId())
                .invocationSeq(record.getInvocationSeq())
                .agentName(record.getAgentName())
                .stepNo(record.getStepNo())
                .callKind(record.getCallKind())
                .streaming(Boolean.TRUE.equals(record.getStreaming()) ? 1 : 0)
                .modelName(record.getModelName())
                .toolCallCount(0)
                .promptTokens(0)
                .completionTokens(0)
                .totalTokens(0)
                .status(ExecutionLedgerConstants.STATUS_RUNNING)
                .startedAt(defaultNow(record.getStartedAt()))
                .build();
        try {
            executionLedgerWriteRepository.insertLlmInvocation(entity);
            markSuccess("createLlmInvocation", null);
            return entity.getId();
        } catch (Exception e) {
            markFailure("createLlmInvocation", record.getRequestId(), record.getRunId(), null, e);
            return null;
        }
    }

    @Override
    public void finishLlmInvocation(LlmInvocationFinishRecord record) {
        if (record == null || record.getLlmInvocationId() == null) {
            return;
        }
        try {
            LocalDateTime finishedAt = defaultNow(record.getFinishedAt());
            executionLedgerWriteRepository.updateLlmInvocationFinish(LlmInvocation.builder()
                    .id(record.getLlmInvocationId())
                    .status(record.getStatus())
                    .responseText(record.getResponseText())
                    .toolCallCount(defaultZero(record.getToolCallCount()))
                    .promptTokens(defaultZero(record.getPromptTokens()))
                    .completionTokens(defaultZero(record.getCompletionTokens()))
                    .totalTokens(defaultZero(record.getTotalTokens()))
                    .finishReason(record.getFinishReason())
                    .errorMsg(trimText(record.getErrorMsg(), 2000))
                    .finishedAt(finishedAt)
                    .build());
            markSuccess("finishLlmInvocation", null);
        } catch (Exception e) {
            markFailure("finishLlmInvocation", record.getRequestId(), null, null, e);
        }
    }

    @Override
    public Map<String, Long> createToolInvocations(ToolInvocationBatchStartRecord record) {
        Map<String, Long> mapping = new LinkedHashMap<>();
        if (record == null || record.getRunId() == null || CollectionUtils.isEmpty(record.getItems())) {
            return mapping;
        }
        for (ToolInvocationBatchStartRecord.Item item : record.getItems()) {
            if (item == null || StringUtils.isBlank(item.getToolCallId())) {
                continue;
            }
            ToolInvocation entity = ToolInvocation.builder()
                    .runId(record.getRunId())
                    .llmInvocationId(record.getLlmInvocationId())
                    .toolCallId(item.getToolCallId())
                    .dispatchIndex(item.getDispatchIndex())
                    .agentName(record.getAgentName())
                    .stepNo(record.getStepNo())
                    .toolName(item.getToolName())
                    .toolProvider(item.getToolProvider())
                    .inputJson(item.getInputJson())
                    .status(ExecutionLedgerConstants.STATUS_RUNNING)
                    .startedAt(defaultNow(item.getStartedAt()))
                    .build();
            try {
                executionLedgerWriteRepository.insertToolInvocation(entity);
                mapping.put(item.getToolCallId(), entity.getId());
                markSuccess("createToolInvocation", null);
            } catch (Exception e) {
                markFailure("createToolInvocation", record.getRequestId(), record.getRunId(), item.getToolCallId(), e);
            }
        }
        return mapping;
    }

    @Override
    public void finishToolInvocation(ToolInvocationFinishRecord record) {
        if (record == null || record.getToolInvocationId() == null) {
            return;
        }
        try {
            executionLedgerWriteRepository.updateToolInvocationFinish(ToolInvocation.builder()
                    .id(record.getToolInvocationId())
                    .status(record.getStatus())
                    .llmObservation(record.getLlmObservation())
                    .errorMsg(trimText(record.getErrorMsg(), 2000))
                    .finishedAt(defaultNow(record.getFinishedAt()))
                    .build());
            persistStructuredOutput(record);
            markSuccess("finishToolInvocation", null);
        } catch (Exception e) {
            markFailure("finishToolInvocation", record.getRequestId(), null, record.getToolCallId(), e);
        }
    }

    /**
     * rich tool 输出和主账本分离，避免结构化结果继续堆在主表。
     */
    private void persistStructuredOutput(ToolInvocationFinishRecord record) {
        if (toolOutputWriter == null
                || record == null
                || !ToolOutputNames.isRichTool(record.getToolName())
                || record.getStructuredOutput() == null) {
            return;
        }
        toolOutputWriter.write(ToolOutputPersistCommand.builder()
                .toolInvocationId(record.getToolInvocationId())
                .runId(record.getRunId())
                .requestId(record.getRequestId())
                .requestSource(ExecutionLedgerConstants.REQUEST_SOURCE_AGENT)
                .sessionId(record.getSessionId())
                .toolCallId(record.getToolCallId())
                .toolName(record.getToolName())
                .status(record.getStatus())
                .errorMsg(record.getErrorMsg())
                .structuredOutput(record.getStructuredOutput())
                .build());
    }

    @Override
    public void recordArtifacts(List<ArtifactRecordCommand> records) {
        try {
            recordArtifactsOrThrow(records);
        } catch (Exception e) {
            String requestId = null;
            Long runId = null;
            if (CollectionUtils.isNotEmpty(records)) {
                for (ArtifactRecordCommand record : records) {
                    if (record == null) {
                        continue;
                    }
                    requestId = requestId == null ? record.getRequestId() : requestId;
                    runId = runId == null ? record.getRunId() : runId;
                }
            }
            markFailure("recordArtifacts", requestId, runId, null, e);
        }
    }

    @Override
    public void recordArtifactsOrThrow(List<ArtifactRecordCommand> records) {
        if (CollectionUtils.isEmpty(records)) {
            return;
        }
        List<ArtifactRecord> entities = new ArrayList<>(records.size());
        for (ArtifactRecordCommand record : records) {
            if (record == null || StringUtils.isBlank(record.getFileName())) {
                continue;
            }
            entities.add(ArtifactRecord.builder()
                    .runId(record.getRunId())
                    .requestId(record.getRequestId())
                    .toolInvocationId(record.getToolInvocationId())
                    .toolCallId(record.getToolCallId())
                    .artifactRole(record.getArtifactRole())
                    .visibility(record.getVisibility())
                    .sourceType(record.getSourceType())
                    .sourceName(record.getSourceName())
                    .fileName(record.getFileName())
                    .storageKey(defaultEmpty(record.getStorageKey()))
                    .downloadUrl(record.getDownloadUrl())
                    .previewUrl(record.getPreviewUrl())
                    .mimeType(record.getMimeType())
                    .fileSize(record.getFileSize())
                    .fileHash(record.getFileHash())
                    .metadataJson(record.getMetadataJson())
                    .build());
        }
        if (entities.isEmpty()) {
            return;
        }
        int inserted = executionLedgerWriteRepository.batchInsertArtifacts(entities);
        if (inserted < entities.size()) {
            throw new IllegalStateException(String.format(
                    "artifact duplicate or ignored, expected=%d, inserted=%d", entities.size(), inserted));
        }
        markSuccess("recordArtifacts", null);
    }

    private int sumPromptTokens(List<LlmInvocation> invocations) {
        int total = 0;
        if (invocations == null) {
            return total;
        }
        for (LlmInvocation invocation : invocations) {
            total += defaultZero(invocation == null ? null : invocation.getPromptTokens());
        }
        return total;
    }

    private int sumCompletionTokens(List<LlmInvocation> invocations) {
        int total = 0;
        if (invocations == null) {
            return total;
        }
        for (LlmInvocation invocation : invocations) {
            total += defaultZero(invocation == null ? null : invocation.getCompletionTokens());
        }
        return total;
    }

    private int sumTotalTokens(List<LlmInvocation> invocations) {
        int total = 0;
        if (invocations == null) {
            return total;
        }
        for (LlmInvocation invocation : invocations) {
            total += defaultZero(invocation == null ? null : invocation.getTotalTokens());
        }
        return total;
    }

    private int sizeOf(List<?> items) {
        return items == null ? 0 : items.size();
    }

    private int defaultZero(Integer value) {
        return value == null ? 0 : value;
    }

    private String defaultEmpty(String value) {
        return value == null ? "" : value;
    }

    private LocalDateTime defaultNow(LocalDateTime value) {
        return value != null ? value : LocalDateTime.now();
    }

    private Long calculateDuration(LocalDateTime startedAt, LocalDateTime finishedAt) {
        if (startedAt == null || finishedAt == null) {
            return null;
        }
        return Duration.between(startedAt, finishedAt).toMillis();
    }

    private String trimText(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    private void markSuccess(String scene, Long durationMs) {
        successCounters.computeIfAbsent(scene, key -> new LongAdder()).increment();
        if (durationMs != null) {
            durationTotals.computeIfAbsent(scene, key -> new LongAdder()).add(durationMs);
        }
    }

    private void markFailure(String scene, String requestId, Long runId, String toolCallId, Exception e) {
        failureCounters.computeIfAbsent(scene, key -> new LongAdder()).increment();
        long success = successCounters.getOrDefault(scene, new LongAdder()).sum();
        long failure = failureCounters.get(scene).sum();
        double successRate = (success + failure) == 0 ? 1D : (double) success / (success + failure);
        log.error("Execution ledger {} failed, requestId={}, runId={}, toolCallId={}, successRate={}",
                scene, requestId, runId, toolCallId, String.format("%.4f", successRate), e);
    }

    /**
     * 会话主表只承接摘要和排序字段，避免再扫一遍 tool/artifact 明细。
     */
    private void upsertSessionHead(DialogueSessionUpsertRecord record) {
        if (record == null || StringUtils.isBlank(record.getSessionId())) {
            return;
        }
        executionLedgerWriteRepository.upsertSession(record);
    }

    private String resolveSessionTitle(String queryText) {
        String normalized = StringUtils.trimToEmpty(queryText);
        if (normalized.isEmpty()) {
            return "新对话";
        }
        return normalized.length() <= 30 ? normalized : normalized.substring(0, 30);
    }

    private int increaseSessionRunCount(String sessionId) {
        return queryRunCount(sessionId) + 1;
    }

    private int queryRunCount(String sessionId) {
        return executionLedgerWriteRepository.queryRunsBySessionId(sessionId).size();
    }

    private int queryFinishedRunCount(String sessionId) {
        return (int) executionLedgerWriteRepository.queryRunsBySessionId(sessionId).stream()
                .filter(item -> item != null && ExecutionLedgerConstants.STATUS_SUCCESS == defaultZero(item.getStatus()))
                .count();
    }

    private int queryFailedRunCount(String sessionId) {
        return (int) executionLedgerWriteRepository.queryRunsBySessionId(sessionId).stream()
                .filter(item -> item != null && isFailedStatus(item.getStatus()))
                .count();
    }

    private boolean isFailedStatus(Integer status) {
        int normalizedStatus = defaultZero(status);
        return normalizedStatus == ExecutionLedgerConstants.STATUS_FAILED
                || normalizedStatus == ExecutionLedgerConstants.STATUS_TIMEOUT
                || normalizedStatus == ExecutionLedgerConstants.STATUS_STOPPED;
    }

    private LocalDateTime resolveSessionStartedAt(String sessionId, LocalDateTime fallback) {
        List<DialogueRunView> runs = executionLedgerWriteRepository.queryRunsBySessionId(sessionId);
        if (CollectionUtils.isEmpty(runs) || runs.get(0) == null || runs.get(0).getStartedAt() == null) {
            return fallback;
        }
        return runs.get(0).getStartedAt();
    }
}
