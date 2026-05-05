package org.wwz.ai.infrastructure.reactor.service.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.ledger.entity.ArtifactRecord;
import org.wwz.ai.domain.agent.ledger.entity.LlmInvocation;
import org.wwz.ai.domain.agent.ledger.entity.ToolInvocation;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunView;
import org.wwz.ai.domain.agent.memory.FileArtifactMemory;
import org.wwz.ai.domain.agent.memory.ReactCycleMemory;
import org.wwz.ai.domain.agent.memory.RunHistoryMemory;
import org.wwz.ai.domain.agent.memory.SessionHistoryMemory;
import org.wwz.ai.domain.agent.memory.ToolCallMemory;
import org.wwz.ai.domain.agent.ledger.ExecutionLedgerQueryService;
import org.wwz.ai.domain.agent.memory.SessionContextMemoryService;
import org.wwz.ai.infrastructure.dao.reactor.IArtifactLedgerDao;
import org.wwz.ai.infrastructure.dao.reactor.ILlmInvocationLedgerDao;
import org.wwz.ai.infrastructure.dao.reactor.IToolInvocationLedgerDao;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 单会话上下文记忆服务实现。
 * Phase 2A 先保留技术执行器形态，由 infrastructure 负责账本 DAO 协作。
 */
@Service
@RequiredArgsConstructor
public class SessionContextMemoryServiceImpl implements SessionContextMemoryService {

    private final ExecutionLedgerQueryService executionLedgerQueryService;
    private final ILlmInvocationLedgerDao llmInvocationLedgerDao;
    private final IToolInvocationLedgerDao toolInvocationLedgerDao;
    private final IArtifactLedgerDao artifactLedgerDao;

    @Override
    public String buildHistoryDialogue(String sessionId, String currentRequestId) {
        if (StringUtils.isBlank(sessionId)) {
            return "";
        }
        SessionHistoryMemory sessionHistoryMemory = assembleSessionHistoryMemory(sessionId, currentRequestId);
        return formatHistoryDialogue(sessionHistoryMemory);
    }

    private SessionHistoryMemory assembleSessionHistoryMemory(String sessionId, String currentRequestId) {
        List<DialogueRunView> orderedRuns = executionLedgerQueryService.querySessionRuns(sessionId).stream()
                .filter(run -> run != null && run.getId() != null)
                .filter(run -> !StringUtils.equals(run.getRequestId(), currentRequestId))
                .toList();
        SessionHistoryMemory sessionHistoryMemory = SessionHistoryMemory.builder()
                .sessionId(sessionId)
                .currentRequestId(currentRequestId)
                .runs(new ArrayList<>())
                .build();
        if (orderedRuns.isEmpty()) {
            return sessionHistoryMemory;
        }

        List<Long> runIds = orderedRuns.stream()
                .map(DialogueRunView::getId)
                .toList();
        List<LlmInvocation> llmInvocations = llmInvocationLedgerDao.queryByRunIds(runIds);
        List<Long> llmInvocationIds = llmInvocations.stream()
                .map(LlmInvocation::getId)
                .filter(id -> id != null)
                .toList();
        List<ToolInvocation> toolInvocations = llmInvocationIds.isEmpty()
                ? List.of()
                : toolInvocationLedgerDao.queryByLlmInvocationIds(llmInvocationIds);
        List<Long> toolInvocationIds = toolInvocations.stream()
                .map(ToolInvocation::getId)
                .filter(id -> id != null)
                .toList();
        List<ArtifactRecord> inputArtifacts = artifactLedgerDao.queryInputArtifactsByRunIds(runIds);
        List<ArtifactRecord> outputArtifacts = toolInvocationIds.isEmpty()
                ? List.of()
                : artifactLedgerDao.queryByToolInvocationIds(toolInvocationIds);

        Map<Long, List<LlmInvocation>> llmInvocationsByRunId = llmInvocations.stream()
                .collect(Collectors.groupingBy(LlmInvocation::getRunId, LinkedHashMap::new, Collectors.toCollection(ArrayList::new)));
        Map<Long, List<ToolInvocation>> toolInvocationsByLlmInvocationId = toolInvocations.stream()
                .collect(Collectors.groupingBy(ToolInvocation::getLlmInvocationId, LinkedHashMap::new, Collectors.toCollection(ArrayList::new)));
        Map<Long, List<FileArtifactMemory>> inputFilesByRunId = inputArtifacts.stream()
                .collect(Collectors.groupingBy(ArtifactRecord::getRunId, LinkedHashMap::new,
                        Collectors.mapping(this::toFileArtifactMemory, Collectors.toCollection(ArrayList::new))));
        Map<Long, List<FileArtifactMemory>> outputFilesByToolInvocationId = outputArtifacts.stream()
                .collect(Collectors.groupingBy(ArtifactRecord::getToolInvocationId, LinkedHashMap::new,
                        Collectors.mapping(this::toFileArtifactMemory, Collectors.toCollection(ArrayList::new))));

        for (DialogueRunView run : orderedRuns) {
            RunHistoryMemory runHistoryMemory = RunHistoryMemory.builder()
                    .runId(run.getId())
                    .requestId(run.getRequestId())
                    .sessionId(run.getSessionId())
                    .entryAgent(run.getEntryAgent())
                    .sessionInputFiles(new ArrayList<>(inputFilesByRunId.getOrDefault(run.getId(), List.of())))
                    .reactCycles(new ArrayList<>())
                    .build();

            // 记忆锚点是 llmInvocation，而不是 run。
            // 一个 llmInvocation 对应一次完整的 ReAct 循环，工具调用只是该循环下的动作明细。
            for (LlmInvocation llmInvocation : llmInvocationsByRunId.getOrDefault(run.getId(), List.of())) {
                ReactCycleMemory cycleMemory = ReactCycleMemory.builder()
                        .runId(run.getId())
                        .requestId(run.getRequestId())
                        .llmInvocationId(llmInvocation.getId())
                        .invocationSeq(llmInvocation.getInvocationSeq())
                        .agentName(llmInvocation.getAgentName())
                        .stepNo(llmInvocation.getStepNo())
                        .thoughtContent(StringUtils.defaultString(llmInvocation.getResponseText()))
                        .toolCalls(new ArrayList<>())
                        .build();
                for (ToolInvocation toolInvocation : toolInvocationsByLlmInvocationId.getOrDefault(llmInvocation.getId(), List.of())) {
                    cycleMemory.getToolCalls().add(ToolCallMemory.builder()
                            .toolInvocationId(toolInvocation.getId())
                            .llmInvocationId(toolInvocation.getLlmInvocationId())
                            .toolCallId(toolInvocation.getToolCallId())
                            .dispatchIndex(toolInvocation.getDispatchIndex())
                            .agentName(toolInvocation.getAgentName())
                            .stepNo(toolInvocation.getStepNo())
                            .toolName(toolInvocation.getToolName())
                            .toolProvider(toolInvocation.getToolProvider())
                            .inputJson(StringUtils.defaultString(toolInvocation.getInputJson()))
                            .llmObservation(StringUtils.defaultString(toolInvocation.getLlmObservation()))
                            .files(new ArrayList<>(outputFilesByToolInvocationId.getOrDefault(toolInvocation.getId(), List.of())))
                            .build());
                }
                runHistoryMemory.getReactCycles().add(cycleMemory);
            }
            sessionHistoryMemory.getRuns().add(runHistoryMemory);
        }
        return sessionHistoryMemory;
    }

    private String formatHistoryDialogue(SessionHistoryMemory sessionHistoryMemory) {
        if (sessionHistoryMemory == null || sessionHistoryMemory.getRuns() == null || sessionHistoryMemory.getRuns().isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder("## 单会话历史记忆\n\n");
        for (RunHistoryMemory run : sessionHistoryMemory.getRuns()) {
            builder.append("### Run ").append(valueOrEmpty(run.getRequestId())).append('\n');
            if (run.getSessionInputFiles() != null && !run.getSessionInputFiles().isEmpty()) {
                builder.append("[Session Input Files]\n");
                for (FileArtifactMemory file : run.getSessionInputFiles()) {
                    builder.append("- fileName=").append(valueOrEmpty(file.getFileName()))
                            .append(", mimeType=").append(valueOrEmpty(file.getMimeType()))
                            .append(", fileSize=").append(valueOrEmpty(file.getFileSize()))
                            .append(", storageKey=").append(valueOrEmpty(file.getStorageKey()))
                            .append(", downloadUrl=").append(valueOrEmpty(file.getDownloadUrl()))
                            .append(", previewUrl=").append(valueOrEmpty(file.getPreviewUrl()))
                            .append('\n');
                }
                builder.append('\n');
            }
            for (ReactCycleMemory cycle : run.getReactCycles()) {
                builder.append("[ReAct Cycle ").append(valueOrEmpty(cycle.getInvocationSeq())).append("]\n");
                builder.append("Thought:\n").append(valueOrEmpty(cycle.getThoughtContent())).append("\n\n");
                builder.append("Tool Calls:\n");
                if (cycle.getToolCalls() == null || cycle.getToolCalls().isEmpty()) {
                    builder.append("- none\n\n");
                    continue;
                }
                int index = 1;
                for (ToolCallMemory toolCall : cycle.getToolCalls()) {
                    builder.append(index++).append(". toolName=").append(valueOrEmpty(toolCall.getToolName())).append('\n');
                    builder.append("   toolProvider=").append(valueOrEmpty(toolCall.getToolProvider())).append('\n');
                    builder.append("   inputJson=").append(valueOrEmpty(toolCall.getInputJson())).append('\n');
                    builder.append("   llmObservation=").append(valueOrEmpty(toolCall.getLlmObservation())).append('\n');
                    builder.append("Files:\n");
                    if (toolCall.getFiles() == null || toolCall.getFiles().isEmpty()) {
                        builder.append("- none\n");
                        continue;
                    }
                    for (FileArtifactMemory file : toolCall.getFiles()) {
                        builder.append("- artifactRole=").append(valueOrEmpty(file.getArtifactRole()))
                                .append(", fileName=").append(valueOrEmpty(file.getFileName()))
                                .append(", mimeType=").append(valueOrEmpty(file.getMimeType()))
                                .append(", fileSize=").append(valueOrEmpty(file.getFileSize()))
                                .append(", storageKey=").append(valueOrEmpty(file.getStorageKey()))
                                .append(", downloadUrl=").append(valueOrEmpty(file.getDownloadUrl()))
                                .append(", previewUrl=").append(valueOrEmpty(file.getPreviewUrl()))
                                .append('\n');
                    }
                }
                builder.append('\n');
            }
        }
        return builder.toString().trim();
    }

    private FileArtifactMemory toFileArtifactMemory(ArtifactRecord artifactRecord) {
        return FileArtifactMemory.builder()
                .artifactId(artifactRecord.getId())
                .runId(artifactRecord.getRunId())
                .requestId(artifactRecord.getRequestId())
                .toolInvocationId(artifactRecord.getToolInvocationId())
                .toolCallId(artifactRecord.getToolCallId())
                .artifactRole(artifactRecord.getArtifactRole())
                .fileName(artifactRecord.getFileName())
                .storageKey(artifactRecord.getStorageKey())
                .downloadUrl(artifactRecord.getDownloadUrl())
                .previewUrl(artifactRecord.getPreviewUrl())
                .mimeType(artifactRecord.getMimeType())
                .fileSize(artifactRecord.getFileSize())
                .build();
    }

    private String valueOrEmpty(String value) {
        return StringUtils.defaultString(value);
    }

    private String valueOrEmpty(Number value) {
        return value == null ? "" : String.valueOf(value);
    }
}
