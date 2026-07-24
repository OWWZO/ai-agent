package org.wwz.ai.infrastructure.reactor.service.impl;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.ledger.ExecutionLedgerQueryService;
import org.wwz.ai.domain.agent.ledger.entity.ArtifactRecord;
import org.wwz.ai.domain.agent.ledger.entity.LlmInvocation;
import org.wwz.ai.domain.agent.ledger.entity.ToolInvocation;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunView;
import org.wwz.ai.domain.agent.memory.FileArtifactMemory;
import org.wwz.ai.domain.agent.memory.ReactCycleMemory;
import org.wwz.ai.domain.agent.memory.RunHistoryMemory;
import org.wwz.ai.domain.agent.memory.SessionContextMemoryService;
import org.wwz.ai.domain.agent.memory.SessionHistoryMemory;
import org.wwz.ai.domain.agent.memory.ToolCallMemory;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.runtime.enums.RoleType;
import org.wwz.ai.domain.agent.runtime.llm.TokenCounter;
import org.wwz.ai.infrastructure.dao.reactor.IArtifactLedgerDao;
import org.wwz.ai.infrastructure.dao.reactor.ILlmInvocationLedgerDao;
import org.wwz.ai.infrastructure.dao.reactor.IToolInvocationLedgerDao;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 单会话上下文记忆服务实现。
 * Phase 2A 先保留技术执行器形态，由 infrastructure 负责账本 DAO 协作。
 */
@Service
public class SessionContextMemoryServiceImpl implements SessionContextMemoryService {

    private static final int DEFAULT_MAX_HISTORY_DIALOGUE_TOKENS = 12000;
    private static final String HISTORY_DIALOGUE_HEADER = "## 单会话历史记忆";
    private static final String HISTORY_DIALOGUE_HEADER_WITH_SEPARATOR = HISTORY_DIALOGUE_HEADER + "\n\n";

    private final ExecutionLedgerQueryService executionLedgerQueryService;
    private final ILlmInvocationLedgerDao llmInvocationLedgerDao;
    private final IToolInvocationLedgerDao toolInvocationLedgerDao;
    private final IArtifactLedgerDao artifactLedgerDao;
    private final TokenCounter tokenCounter;
    private final int maxHistoryDialogueTokens;

    public SessionContextMemoryServiceImpl(ExecutionLedgerQueryService executionLedgerQueryService,
                                           ILlmInvocationLedgerDao llmInvocationLedgerDao,
                                           IToolInvocationLedgerDao toolInvocationLedgerDao,
                                           IArtifactLedgerDao artifactLedgerDao) {
        this(
                executionLedgerQueryService,
                llmInvocationLedgerDao,
                toolInvocationLedgerDao,
                artifactLedgerDao,
                DEFAULT_MAX_HISTORY_DIALOGUE_TOKENS
        );
    }

    @Autowired
    public SessionContextMemoryServiceImpl(ExecutionLedgerQueryService executionLedgerQueryService,
                                           ILlmInvocationLedgerDao llmInvocationLedgerDao,
                                           IToolInvocationLedgerDao toolInvocationLedgerDao,
                                           IArtifactLedgerDao artifactLedgerDao,
                                           @Value("${autobots.autoagent.history-dialogue.max-tokens:12000}") int maxHistoryDialogueTokens) {
        this.executionLedgerQueryService = executionLedgerQueryService;
        this.llmInvocationLedgerDao = llmInvocationLedgerDao;
        this.toolInvocationLedgerDao = toolInvocationLedgerDao;
        this.artifactLedgerDao = artifactLedgerDao;
        this.tokenCounter = new TokenCounter();
        this.maxHistoryDialogueTokens = normalizeMaxHistoryDialogueTokens(maxHistoryDialogueTokens);
    }

    @Override
    public String buildHistoryDialogue(String sessionId, String currentRequestId) {
        if (StringUtils.isBlank(sessionId)) {
            return "";
        }
        SessionHistoryMemory sessionHistoryMemory = assembleSessionHistoryMemory(sessionId, currentRequestId);
        return formatHistoryDialogueWithinTokenBudget(sessionHistoryMemory);
    }

    @Override
    public List<Message> hydrateWorkingMessages(String sessionId, String currentRequestId) {
        if (StringUtils.isBlank(sessionId)) {
            return List.of();
        }
        SessionHistoryMemory sessionHistoryMemory = assembleSessionHistoryMemory(sessionId, currentRequestId);
        return formatWorkingMessages(sessionHistoryMemory);
    }

    /**
     * 将账本历史投影为模型可见 Message 链（append-only，不丢最旧 run，避免前缀重写导致 prompt cache miss）。
     */
    private List<Message> formatWorkingMessages(SessionHistoryMemory sessionHistoryMemory) {
        if (sessionHistoryMemory == null || sessionHistoryMemory.getRuns() == null || sessionHistoryMemory.getRuns().isEmpty()) {
            return List.of();
        }
        List<Message> all = new ArrayList<>();
        for (RunHistoryMemory run : sessionHistoryMemory.getRuns()) {
            List<Message> block = toWorkingMessages(run);
            if (block != null && !block.isEmpty()) {
                all.addAll(block);
            }
        }
        return all;
    }

    private List<Message> toWorkingMessages(RunHistoryMemory run) {
        if (run == null) {
            return List.of();
        }
        List<Message> messages = new ArrayList<>();
        // 1) 用户 query
        if (StringUtils.isNotBlank(run.getQueryText())) {
            messages.add(Message.userMessage(run.getQueryText(), null));
        } else if (StringUtils.isNotBlank(run.getRequestId())) {
            // query 缺失时仍给一个锚点，避免纯 tool 轨迹悬空
            messages.add(Message.userMessage("[previous request] " + run.getRequestId(), null));
        }

        // 2) 每个 ReAct cycle → assistant (+ tool calls) + tool observations
        if (run.getReactCycles() != null) {
            int cycleIndex = 0;
            for (ReactCycleMemory cycle : run.getReactCycles()) {
                cycleIndex++;
                if (cycle == null) {
                    continue;
                }
                List<ToolCall> toolCalls = new ArrayList<>();
                if (cycle.getToolCalls() != null) {
                    int dispatch = 0;
                    for (ToolCallMemory tool : cycle.getToolCalls()) {
                        if (tool == null) {
                            continue;
                        }
                        dispatch++;
                        String callId = StringUtils.isNotBlank(tool.getToolCallId())
                                ? tool.getToolCallId()
                                : "hist-" + valueOrEmpty(run.getRunId()) + "-" + cycleIndex + "-" + dispatch;
                        toolCalls.add(ToolCall.builder()
                                .id(callId)
                                .type("function")
                                .function(ToolCall.Function.builder()
                                        .name(StringUtils.defaultString(tool.getToolName(), "unknown_tool"))
                                        .arguments(StringUtils.defaultString(tool.getInputJson(), "{}"))
                                        .build())
                                .build());
                    }
                }
                String thought = StringUtils.defaultString(cycle.getThoughtContent());
                if (!toolCalls.isEmpty()) {
                    messages.add(Message.fromToolCalls(thought, toolCalls));
                    // tool results in same order
                    int dispatch = 0;
                    for (ToolCallMemory tool : cycle.getToolCalls()) {
                        if (tool == null) {
                            continue;
                        }
                        dispatch++;
                        String callId = StringUtils.isNotBlank(tool.getToolCallId())
                                ? tool.getToolCallId()
                                : "hist-" + valueOrEmpty(run.getRunId()) + "-" + cycleIndex + "-" + dispatch;
                        String observation = StringUtils.defaultString(tool.getLlmObservation());
                        if (tool.getFiles() != null && !tool.getFiles().isEmpty()) {
                            StringBuilder fileHint = new StringBuilder();
                            for (FileArtifactMemory file : tool.getFiles()) {
                                if (file == null || StringUtils.isBlank(file.getFileName())) {
                                    continue;
                                }
                                if (fileHint.length() > 0) {
                                    fileHint.append(", ");
                                }
                                fileHint.append(file.getFileName());
                            }
                            if (fileHint.length() > 0) {
                                observation = observation + (observation.isBlank() ? "" : "\n") + "产出文件: " + fileHint;
                            }
                        }
                        messages.add(Message.toolMessage(observation, callId, null));
                    }
                } else if (StringUtils.isNotBlank(thought)) {
                    messages.add(Message.assistantMessage(thought, null));
                }
            }
        }

        // 3) final summary 作为收尾 assistant（与最后 thought 不同才追加，减少重复）
        if (StringUtils.isNotBlank(run.getFinalSummaryText())) {
            String summary = run.getFinalSummaryText().trim();
            boolean sameAsLastThought = false;
            if (!messages.isEmpty()) {
                Message last = messages.get(messages.size() - 1);
                if (last != null && last.getContent() != null && summary.equals(last.getContent().trim())) {
                    sameAsLastThought = true;
                }
            }
            if (!sameAsLastThought) {
                messages.add(Message.assistantMessage(summary, null));
            }
        }

        return messages;
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
                    .queryText(run.getQueryText())
                    .finalSummaryText(run.getFinalSummaryText())
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

    private String formatHistoryDialogueWithinTokenBudget(SessionHistoryMemory sessionHistoryMemory) {
        if (sessionHistoryMemory == null || sessionHistoryMemory.getRuns() == null || sessionHistoryMemory.getRuns().isEmpty()) {
            return "";
        }
        LinkedList<String> keptRunBlocks = new LinkedList<>();
        for (int index = sessionHistoryMemory.getRuns().size() - 1; index >= 0; index--) {
            String runBlock = formatRunHistory(sessionHistoryMemory.getRuns().get(index));
            keptRunBlocks.addFirst(runBlock);
            String candidateHistoryDialogue = buildHistoryDialogueText(keptRunBlocks);
            if (tokenCounter.countText(candidateHistoryDialogue) <= maxHistoryDialogueTokens) {
                continue;
            }

            keptRunBlocks.removeFirst();
            if (keptRunBlocks.isEmpty()) {
                String truncatedLatestRunBlock = truncateRunBlockToFit(runBlock);
                if (StringUtils.isNotBlank(truncatedLatestRunBlock)) {
                    keptRunBlocks.add(truncatedLatestRunBlock);
                }
            }
            break;
        }
        return buildHistoryDialogueText(keptRunBlocks);
    }

    private String buildHistoryDialogueText(List<String> runBlocks) {
        if (runBlocks == null || runBlocks.isEmpty()) {
            return HISTORY_DIALOGUE_HEADER;
        }
        return HISTORY_DIALOGUE_HEADER_WITH_SEPARATOR + String.join("\n\n", runBlocks);
    }

    private String formatRunHistory(RunHistoryMemory run) {
        StringBuilder builder = new StringBuilder();
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
        return builder.toString().trim();
    }

    private String truncateRunBlockToFit(String runBlock) {
        int remainingTokens = maxHistoryDialogueTokens - tokenCounter.countText(HISTORY_DIALOGUE_HEADER_WITH_SEPARATOR);
        if (remainingTokens <= 0 || StringUtils.isBlank(runBlock)) {
            return "";
        }
        int truncateLength = Math.min(runBlock.length(), remainingTokens);

        // 当前 TokenCounter 仍按字符数近似计算，因此这里按字符裁剪即可稳定满足预算上限。
        return runBlock.substring(0, truncateLength).trim();
    }

    private int normalizeMaxHistoryDialogueTokens(int configuredMaxTokens) {
        return configuredMaxTokens > 0 ? configuredMaxTokens : DEFAULT_MAX_HISTORY_DIALOGUE_TOKENS;
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
