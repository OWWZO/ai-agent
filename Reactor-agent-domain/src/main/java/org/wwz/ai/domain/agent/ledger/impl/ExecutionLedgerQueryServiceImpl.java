package org.wwz.ai.domain.agent.ledger.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.ledger.IExecutionLedgerReadRepository;
import org.wwz.ai.domain.agent.ledger.entity.ArtifactRecord;
import org.wwz.ai.domain.agent.ledger.entity.DialogueRun;
import org.wwz.ai.domain.agent.ledger.entity.LlmInvocation;
import org.wwz.ai.domain.agent.ledger.entity.ToolInvocation;
import org.wwz.ai.domain.agent.ledger.model.ArtifactView;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunView;
import org.wwz.ai.domain.agent.ledger.model.DialogueSessionView;
import org.wwz.ai.domain.agent.ledger.model.ExecutionRunDetail;
import org.wwz.ai.domain.agent.ledger.model.LlmInvocationView;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationView;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolOutputNames;
import org.wwz.ai.domain.agent.ledger.ExecutionLedgerQueryService;
import org.wwz.ai.domain.agent.ledger.tooloutput.ToolOutputReader;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 执行账本内部查询服务。
 */
@Service
@RequiredArgsConstructor
public class ExecutionLedgerQueryServiceImpl implements ExecutionLedgerQueryService {

    private final IExecutionLedgerReadRepository executionLedgerReadRepository;
    private final ToolOutputReader toolOutputReader;

    @Override
    public ExecutionRunDetail queryRunDetail(String requestId) {
        if (StringUtils.isBlank(requestId)) {
            return null;
        }
        DialogueRun run = executionLedgerReadRepository.queryRunByRequestId(requestId);
        if (run == null) {
            return null;
        }
        List<LlmInvocation> llmInvocations = executionLedgerReadRepository.queryLlmInvocationsByRunId(run.getId());
        List<ToolInvocation> toolInvocations = executionLedgerReadRepository.queryToolInvocationsByRunId(run.getId());
        List<ArtifactRecord> artifacts = executionLedgerReadRepository.queryArtifactsByRunId(run.getId());
        return ExecutionRunDetail.builder()
                .run(toRunView(run))
                .llmInvocations(toLlmViews(llmInvocations))
                .toolInvocations(toToolViews(toolInvocations, run.getRequestId(), run.getSessionId(), artifacts))
                .artifacts(toArtifactViews(artifacts))
                .build();
    }

    @Override
    public List<ToolInvocationView> queryRecentToolInvocations(String toolName, int limit) {
        if (StringUtils.isBlank(toolName)) {
            return List.of();
        }
        return enrichStructuredOutputs(executionLedgerReadRepository.queryRecentToolInvocations(toolName, normalizeLimit(limit)));
    }

    @Override
    public List<DialogueRunView> queryRecentSessionRuns(String sessionId, int limit) {
        if (StringUtils.isBlank(sessionId)) {
            return List.of();
        }
        List<DialogueRunView> runViews = executionLedgerReadRepository.queryRecentRunsBySessionId(sessionId, normalizeLimit(limit));
        return attachArtifactSummaries(runViews);
    }

    @Override
    public List<DialogueRunView> querySessionRuns(String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            return List.of();
        }
        return attachArtifactSummaries(executionLedgerReadRepository.queryRunsBySessionId(sessionId));
    }

    @Override
    public DialogueSessionView querySession(String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            return null;
        }
        return executionLedgerReadRepository.querySession(sessionId);
    }

    @Override
    public List<DialogueSessionView> queryRecentSessions(int limit) {
        return executionLedgerReadRepository.queryRecentSessions(normalizeLimit(limit));
    }

    @Override
    public DialogueSessionView querySession(String visitorId, String sessionId) {
        if (StringUtils.isAnyBlank(visitorId, sessionId)) {
            return null;
        }
        return executionLedgerReadRepository.querySession(visitorId, sessionId);
    }

    @Override
    public List<DialogueSessionView> queryRecentSessions(String visitorId, int limit) {
        if (StringUtils.isBlank(visitorId)) {
            return List.of();
        }
        return executionLedgerReadRepository.queryRecentSessions(visitorId, normalizeLimit(limit));
    }

    private List<DialogueRunView> attachArtifactSummaries(List<DialogueRunView> runViews) {
        if (CollectionUtils.isEmpty(runViews)) {
            return runViews;
        }
        List<Long> runIds = runViews.stream()
                .map(DialogueRunView::getId)
                .filter(id -> id != null)
                .toList();
        if (runIds.isEmpty()) {
            return runViews;
        }
        // 会话摘要列表只需要轻量文件概览，这里统一补到 run 视图上，
        // 避免 controller / UI 再额外扫 artifact 表做第二次拼装。
        Map<Long, List<ArtifactView>> artifactViewsByRunId = executionLedgerReadRepository.queryArtifactsByRunIds(runIds).stream()
                .collect(Collectors.groupingBy(
                        ArtifactRecord::getRunId,
                        LinkedHashMap::new,
                        Collectors.mapping(this::toArtifactView, Collectors.toCollection(ArrayList::new))));
        for (DialogueRunView runView : runViews) {
            runView.setArtifactSummaries(artifactViewsByRunId.getOrDefault(runView.getId(), List.of()));
        }
        return runViews;
    }

    private int normalizeLimit(int limit) {
        // 最近会话列表和最近 run 摘要统一走同一套 limit 归一规则，
        // 默认 20、上限 100，避免不同入口出现分页语义分叉。
        if (limit <= 0) {
            return 20;
        }
        return Math.min(limit, 100);
    }

    private DialogueRunView toRunView(DialogueRun run) {
        if (run == null) {
            return null;
        }
        return DialogueRunView.builder()
                .id(run.getId())
                .runUid(run.getRunUid())
                .requestId(run.getRequestId())
                .sessionId(run.getSessionId())
                .visitorId(run.getVisitorId())
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

    private List<LlmInvocationView> toLlmViews(List<LlmInvocation> invocations) {
        if (invocations == null) {
            return List.of();
        }
        List<LlmInvocationView> views = new ArrayList<>(invocations.size());
        for (LlmInvocation invocation : invocations) {
            views.add(LlmInvocationView.builder()
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
                    .build());
        }
        return views;
    }

    private List<ToolInvocationView> toToolViews(List<ToolInvocation> invocations,
                                                 String requestId,
                                                 String sessionId,
                                                 List<ArtifactRecord> artifacts) {
        if (invocations == null) {
            return List.of();
        }
        Map<Long, Integer> artifactCountByToolInvocationId = new LinkedHashMap<>();
        if (artifacts != null) {
            for (ArtifactRecord artifact : artifacts) {
                if (artifact == null || artifact.getToolInvocationId() == null) {
                    continue;
                }
                artifactCountByToolInvocationId.merge(artifact.getToolInvocationId(), 1, Integer::sum);
            }
        }
        List<ToolInvocationView> views = new ArrayList<>(invocations.size());
        for (ToolInvocation invocation : invocations) {
            views.add(ToolInvocationView.builder()
                    .id(invocation.getId())
                    .runId(invocation.getRunId())
                    .llmInvocationId(invocation.getLlmInvocationId())
                    .requestId(requestId)
                    .sessionId(sessionId)
                    .toolCallId(invocation.getToolCallId())
                    .dispatchIndex(invocation.getDispatchIndex())
                    .agentName(invocation.getAgentName())
                    .stepNo(invocation.getStepNo())
                    .toolName(invocation.getToolName())
                    .toolProvider(invocation.getToolProvider())
                    .inputJson(invocation.getInputJson())
                    .llmObservation(invocation.getLlmObservation())
                    .status(invocation.getStatus())
                    .errorMsg(invocation.getErrorMsg())
                    .durationMs(invocation.getDurationMs())
                    .artifactCount(artifactCountByToolInvocationId.getOrDefault(invocation.getId(), 0))
                    .startedAt(invocation.getStartedAt())
                    .finishedAt(invocation.getFinishedAt())
                    .createTime(invocation.getCreateTime())
                    .build());
        }
        return enrichStructuredOutputs(views);
    }

    private List<ToolInvocationView> enrichStructuredOutputs(List<ToolInvocationView> views) {
        if (views == null || toolOutputReader == null) {
            return views == null ? List.of() : views;
        }
        for (ToolInvocationView view : views) {
            if (view == null
                    || view.getId() == null
                    || !ToolOutputNames.isRichTool(view.getToolName())) {
                continue;
            }
            toolOutputReader.readByInvocationId(view.getToolName(), view.getId())
                    .ifPresent(view::setStructuredOutput);
        }
        return views;
    }

    private List<ArtifactView> toArtifactViews(List<ArtifactRecord> artifacts) {
        if (artifacts == null) {
            return List.of();
        }
        List<ArtifactView> views = new ArrayList<>(artifacts.size());
        for (ArtifactRecord artifact : artifacts) {
            views.add(toArtifactView(artifact));
        }
        return views;
    }

    private ArtifactView toArtifactView(ArtifactRecord artifact) {
        return ArtifactView.builder()
                .id(artifact.getId())
                .runId(artifact.getRunId())
                .requestId(artifact.getRequestId())
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
                .build();
    }
}
