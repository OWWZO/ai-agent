package org.wwz.ai.domain.agent.runtime.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.ledger.model.ArtifactRecordCommand;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationBatchStartRecord;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationFinishRecord;
import org.wwz.ai.domain.agent.ledger.model.replay.ReplayTiming;
import org.wwz.ai.domain.agent.runtime.dto.File;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.runtime.subagent.BackgroundSubAgentExecutor;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.tool.common.AgentDispatchTool;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.TaskToolNames;
import org.wwz.ai.domain.agent.runtime.tool.deferred.DeferredToolCatalog;
import org.wwz.ai.domain.agent.runtime.tool.deferred.DeferredToolSource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具调用的 Execution Ledger 写入：预登记、终态、产物。
 * <p>
 * 所有写入都以「有活跃 run」为闸门；缺少 runId 或 toolInvocationId 时静默跳过，
 * 避免写出无法结算的孤儿行。
 */
final class ToolLedgerRecorder {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final BaseAgent agent;
    private final ToolTimingTracker timings;

    ToolLedgerRecorder(BaseAgent agent, ToolTimingTracker timings) {
        this.agent = agent;
        this.timings = timings;
    }

    /**
     * 为尚未登记的 tool call 预建账本行。
     * <p>
     * 已登记的沿用 {@link AgentRunState} 里的 ID，只把缺的部分交给 recorder，
     * 避免同一 toolCallId 重复建行。
     */
    Map<String, Long> ensureIds(Map<String, ToolDispatch> dispatches, List<ToolCall> commands) {
        AgentContext context = agent.getContext();
        if (context == null || context.getAgentRunState() == null || commands == null || commands.isEmpty()) {
            return Map.of();
        }
        Map<String, Long> existing = new LinkedHashMap<>();
        List<ToolCall> missingCommands = new ArrayList<>();
        for (ToolCall command : commands) {
            if (command == null || StringUtils.isBlank(command.getId())) {
                continue;
            }
            Long existingInvocationId = context.getAgentRunState().resolveToolInvocationId(command.getId());
            if (existingInvocationId != null) {
                existing.put(command.getId(), existingInvocationId);
            } else {
                missingCommands.add(command);
            }
        }
        if (missingCommands.isEmpty()) {
            return existing;
        }
        Map<String, Long> created = preRegister(missingCommands, dispatches);
        if (existing.isEmpty()) {
            return created;
        }
        if (created.isEmpty()) {
            return existing;
        }
        existing.putAll(created);
        return existing;
    }

    /** 只建缺失的账本行，不合并已存在的 ID。 */
    Map<String, Long> preRegister(List<ToolCall> commands, Map<String, ToolDispatch> dispatches) {
        timings.start(commands);
        AgentContext context = agent.getContext();
        if (context == null || !context.hasActiveLedgerRun() || context.getAgentRunState() == null) {
            return Map.of();
        }
        Long llmInvocationId = context.getAgentRunState().getCurrentLlmInvocationId();
        if (llmInvocationId == null) {
            return Map.of();
        }
        List<ToolInvocationBatchStartRecord.Item> items = new ArrayList<>(commands.size());
        int dispatchIndex = 1;
        for (ToolCall command : commands) {
            if (command == null || command.getFunction() == null || StringUtils.isBlank(command.getFunction().getName())) {
                continue;
            }
            ToolDispatch dispatch = dispatches == null ? null : dispatches.get(command.getId());
            String toolName = dispatch == null ? command.getFunction().getName() : dispatch.toolName;
            items.add(ToolInvocationBatchStartRecord.Item.builder()
                    .toolCallId(command.getId())
                    .parentToolCallId(context.getParentToolUseId())
                    .subAgentId(context.getSubAgentId())
                    .subAgentType(context.getSubAgentType())
                    .subAgentDescription(context.getSubAgentDescription())
                    .dispatchIndex(dispatchIndex++)
                    .toolName(toolName)
                    .toolProvider(providerOf(toolName))
                    .inputJson(dispatch == null ? ToolCallJson.normalize(command.getFunction().getArguments())
                            : dispatch.inputJson)
                    .startedAt(timings.resolve(command).getStartedAt())
                    .build());
        }
        if (items.isEmpty()) {
            return Map.of();
        }
        return context.getExecutionRecorder().createToolInvocations(ToolInvocationBatchStartRecord.builder()
                .runId(context.getAgentRunState().getRunId())
                .requestId(context.getRequestId())
                .llmInvocationId(llmInvocationId)
                .agentName(agent.getName())
                .stepNo(agent.getCurrentStep())
                .items(items)
                .build());
    }

    void finish(ToolCall command, ToolDispatch dispatch, ToolExecutionOutcome outcome) {
        AgentContext context = agent.getContext();
        if (context == null || !context.hasActiveLedgerRun() || context.getAgentRunState() == null || command == null) {
            return;
        }
        Long toolInvocationId = context.getAgentRunState().resolveToolInvocationId(command.getId());
        if (toolInvocationId == null) {
            return;
        }
        String toolName = dispatch == null || command.getFunction() == null
                ? (command.getFunction() == null ? null : command.getFunction().getName())
                : dispatch.toolName;
        ReplayTiming timing = outcome == null ? timings.resolve(command) : outcome.getTiming();
        LocalDateTime finishedAt = timing == null || timing.getFinishedAt() == null
                ? LocalDateTime.now()
                : timing.getFinishedAt();
        String observation = outcome == null ? null : StringUtils.defaultIfBlank(
                outcome.getLedgerObservation(), outcome.getLlmObservation());
        // 后台 ACK 不是工具完成：子 Agent 还在后台跑。此时必须保持 status=RUNNING、
        // finished_at=null，否则 settle（persistLedgerSettlement）的终态更新会被
        // updateToolInvocationFinish 的守卫（status=0 AND finished_at IS NULL）挡掉，
        // 结算 observation 静默丢失，历史回放把陈旧 ACK 当"运行中"展示。
        boolean backgroundAck =
                (AgentDispatchTool.NAME.equals(toolName) || TaskToolNames.SEND_MESSAGE.equals(toolName))
                        && BackgroundSubAgentExecutor.isBackgroundAckObservation(observation);
        int ledgerStatus = backgroundAck
                ? ExecutionLedgerConstants.STATUS_RUNNING
                : ToolExecutionPipeline.ledgerStatus(outcome);
        LocalDateTime ledgerFinishedAt = backgroundAck ? null : finishedAt;
        context.getExecutionRecorder().finishToolInvocation(ToolInvocationFinishRecord.builder()
                .toolInvocationId(toolInvocationId)
                .runId(context.getAgentRunState().getRunId())
                .requestId(context.getRequestId())
                .sessionId(context.getSessionId())
                .toolCallId(command.getId())
                .toolName(toolName)
                .status(ledgerStatus)
                .llmObservation(observation)
                .structuredOutput(outcome == null ? null : outcome.getStructuredOutput())
                .errorMsg(outcome == null ? null : outcome.getErrorMsg())
                .finishedAt(ledgerFinishedAt)
                .build());
        if (command.getFunction() != null
                && (AgentDispatchTool.NAME.equals(command.getFunction().getName())
                || TaskToolNames.SEND_MESSAGE.equals(command.getFunction().getName()))) {
            BackgroundSubAgentExecutor.settleLedgerIfTerminal(
                    context,
                    command.getId(),
                    command.getFunction().getName(),
                    outcome == null ? null : outcome.getLlmObservation());
        }
    }

    void recordArtifacts(ToolCall command) {
        AgentContext context = agent.getContext();
        if (context == null || !context.hasActiveLedgerRun() || context.getAgentRunState() == null || command == null) {
            return;
        }
        Long toolInvocationId = context.getAgentRunState().resolveToolInvocationId(command.getId());
        if (toolInvocationId == null) {
            return;
        }
        List<ArtifactRecordCommand> artifactCommands = new ArrayList<>();
        for (var binding : context.getArtifactBindingsByToolCallId(command.getId())) {
            if (binding == null || binding.getSource() == null || binding.getFile() == null) {
                continue;
            }
            File file = binding.getFile();
            artifactCommands.add(ArtifactRecordCommand.builder()
                    .runId(context.getAgentRunState().getRunId())
                    .requestId(context.getRequestId())
                    .toolInvocationId(toolInvocationId)
                    .toolCallId(command.getId())
                    .artifactRole(ExecutionLedgerConstants.ARTIFACT_ROLE_OUTPUT)
                    .visibility(binding.isInternalFile()
                            ? ExecutionLedgerConstants.VISIBILITY_INTERNAL
                            : ExecutionLedgerConstants.VISIBILITY_VISIBLE)
                    .sourceType(ExecutionLedgerConstants.SOURCE_TYPE_TOOL_OUTPUT)
                    .sourceName(binding.getSource().getToolName())
                    .fileName(file.getFileName())
                    .storageKey(resolveStorageKey(file))
                    .downloadUrl(file.getOssUrl())
                    .previewUrl(file.getDomainUrl())
                    .fileSize(file.getFileSize() == null ? null : file.getFileSize().longValue())
                    .metadataJson(buildArtifactMetadata(file))
                    .build());
        }
        if (!artifactCommands.isEmpty()) {
            context.getExecutionRecorder().recordArtifacts(artifactCommands);
        }
    }

    /** MCP（含延迟 MCP）与本地工具在账本里用不同 provider 区分。 */
    String providerOf(String toolName) {
        ToolCollection availableTools = agent.getAvailableTools();
        if (availableTools == null || StringUtils.isBlank(toolName)) {
            return ExecutionLedgerConstants.TOOL_PROVIDER_LOCAL;
        }
        if (availableTools.getMcpToolMap() != null && availableTools.getMcpToolMap().containsKey(toolName)) {
            return ExecutionLedgerConstants.TOOL_PROVIDER_MCP;
        }
        DeferredToolCatalog catalog = availableTools.getDeferredToolCatalog();
        if (catalog != null) {
            var entry = catalog.get(toolName);
            if (entry != null && entry.getSource() == DeferredToolSource.MCP) {
                return ExecutionLedgerConstants.TOOL_PROVIDER_MCP;
            }
        }
        return ExecutionLedgerConstants.TOOL_PROVIDER_LOCAL;
    }

    private static String resolveStorageKey(File file) {
        if (file == null) {
            return "";
        }
        if (StringUtils.isNotBlank(file.getOriginOssUrl())) {
            return file.getOriginOssUrl();
        }
        if (StringUtils.isNotBlank(file.getOssUrl())) {
            return file.getOssUrl();
        }
        if (StringUtils.isNotBlank(file.getOriginDomainUrl())) {
            return file.getOriginDomainUrl();
        }
        if (StringUtils.isNotBlank(file.getDomainUrl())) {
            return file.getDomainUrl();
        }
        return StringUtils.defaultString(file.getFileName());
    }

    private static String buildArtifactMetadata(File file) {
        if (file == null) {
            return null;
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (StringUtils.isNotBlank(file.getDescription())) {
            metadata.put("description", file.getDescription());
        }
        if (StringUtils.isNotBlank(file.getOriginFileName())) {
            metadata.put("originFileName", file.getOriginFileName());
        }
        if (StringUtils.isNotBlank(file.getRelativePath())) {
            metadata.put("relativePath", file.getRelativePath());
        }
        if (StringUtils.isNotBlank(file.getOriginDomainUrl())) {
            metadata.put("originDomainUrl", file.getOriginDomainUrl());
        }
        if (metadata.isEmpty()) {
            return null;
        }
        try {
            return JSON.writeValueAsString(metadata);
        } catch (Exception ignore) {
            return null;
        }
    }
}
