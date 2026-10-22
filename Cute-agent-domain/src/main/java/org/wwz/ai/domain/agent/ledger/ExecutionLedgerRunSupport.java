package org.wwz.ai.domain.agent.ledger;

import com.alibaba.fastjson.JSON;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.command.AgentExecutionFile;
import org.wwz.ai.domain.agent.ledger.model.ArtifactRecordCommand;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunFinishRecord;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunStartRecord;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.runtime.command.AgentExecutionCommand;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 执行账本运行态辅助方法。
 * 统一收口 run 的启动、输入产物登记和结束写回，避免多条执行链路重复实现。
 */
public final class ExecutionLedgerRunSupport {

    private ExecutionLedgerRunSupport() {
    }

    /**
     * 创建 run 并登记本次请求携带的输入文件。
     */
    public static void initializeRun(AgentExecutionRecorder recorder,
                                     AgentContext agentContext,
                                     AgentExecutionCommand request,
                                     String entryAgent) {
        if (recorder == null || agentContext == null || request == null) {
            return;
        }
        Long runId = recorder.createRun(DialogueRunStartRecord.builder()
                .runUid(request.getRequestId())
                .requestId(request.getRequestId())
                .sessionId(request.getSessionId())
                .userId(request.getUserId())
                .entryAgent(entryAgent)
                .queryText(request.getQuery())
                .build());
        agentContext.activateLedgerRun(runId, request.getRequestId());
        recordInputArtifacts(recorder, request.getSessionFiles(), runId, request.getRequestId());
    }

    /**
     * 结束当前 run。
     * 成功态写入 finalSummaryText，非成功态写入 errorCode / errorMsg。
     */
    public static void finishRun(AgentContext agentContext,
                                 int status,
                                 String finalSummaryText,
                                 String errorCode,
                                 String errorMsg) {
        if (agentContext != null) {
            // 先打内存终态，避免后台子 Agent 在 ledger 写回前误关父 SSE。
            agentContext.markTurnClosed();
        }
        if (!hasActiveRun(agentContext) || agentContext.getExecutionRecorder() == null) {
            return;
        }
        agentContext.getExecutionRecorder().finishRun(DialogueRunFinishRecord.builder()
                .runId(agentContext.getAgentRunState().getRunId())
                .requestId(agentContext.getRequestId())
                .status(status)
                .finalSummaryText(finalSummaryText)
                .errorCode(errorCode)
                .errorMsg(errorMsg)
                .build());
    }

    /**
     * 子智能体独立 run：LLM / tool / artifact 全部挂到子会话自己的 run 上，
     * 不计入父 run 的 llm_call_count / tool_call_count。
     * <p>
     * 与 {@link #initializeRun} 的区别：不走 {@code AgentExecutionCommand}，
     * 只按已就绪的 AgentContext 落 run，因此复用者只需提供 recorder 与已构建好的子上下文。
     * recorder 为无状态账本写入服务，父子可安全共享同一实例。
     */
    public static void initializeSubAgentRun(AgentExecutionRecorder recorder,
                                             AgentContext agentContext,
                                             String entryAgent,
                                             String queryText) {
        if (recorder == null || agentContext == null) {
            return;
        }
        Long runId = recorder.createRun(DialogueRunStartRecord.builder()
                .runUid(agentContext.getRequestId())
                .requestId(agentContext.getRequestId())
                .sessionId(agentContext.getSessionId())
                .userId(agentContext.getUserId())
                .entryAgent(entryAgent)
                .queryText(queryText)
                .build());
        // runId 为空表示落库失败；此时不激活 run，子 Agent 的 LLM/tool 记账自然跳过
        // （LLM / ToolExecutionPipeline 均以 hasActiveLedgerRun 为闸门），不会误记到父 run。
        agentContext.activateLedgerRun(runId, agentContext.getRequestId());
    }

    private static void recordInputArtifacts(AgentExecutionRecorder recorder,
                                              List<AgentExecutionFile> sessionFiles,
                                             Long runId,
                                             String requestId) {
        List<ArtifactRecordCommand> inputArtifacts = buildInputArtifacts(sessionFiles, runId, requestId);
        if (!inputArtifacts.isEmpty()) {
            recorder.recordArtifacts(inputArtifacts);
        }
    }

    private static List<ArtifactRecordCommand> buildInputArtifacts(List<AgentExecutionFile> sessionFiles,
                                                                   Long runId,
                                                                   String requestId) {
        if (runId == null || sessionFiles == null || sessionFiles.isEmpty()) {
            return List.of();
        }
        List<ArtifactRecordCommand> records = new ArrayList<>(sessionFiles.size());
        for (AgentExecutionFile sessionFile : sessionFiles) {
            if (!hasValidFileName(sessionFile)) {
                continue;
            }
            records.add(ArtifactRecordCommand.builder()
                    .runId(runId)
                    .requestId(requestId)
                    .artifactRole(ExecutionLedgerConstants.ARTIFACT_ROLE_INPUT)
                    .visibility(ExecutionLedgerConstants.VISIBILITY_VISIBLE)
                    .sourceType(ExecutionLedgerConstants.SOURCE_TYPE_USER_UPLOAD)
                    .sourceName(ExecutionLedgerConstants.SOURCE_TYPE_USER_UPLOAD)
                    .fileName(sessionFile.getFileName())
                    .storageKey(resolveStorageKey(sessionFile))
                    .downloadUrl(sessionFile.getOssUrl())
                    .previewUrl(sessionFile.getDomainUrl())
                    .mimeType(sessionFile.getMimeType())
                    .fileSize(sessionFile.getFileSize() == null ? null : sessionFile.getFileSize().longValue())
                    .metadataJson(buildInputMetadata(sessionFile))
                    .build());
        }
        return records;
    }

    private static boolean hasActiveRun(AgentContext agentContext) {
        return agentContext != null
                && agentContext.hasActiveLedgerRun()
                && agentContext.getAgentRunState() != null;
    }

    private static boolean hasValidFileName(AgentExecutionFile sessionFile) {
        return sessionFile != null && StringUtils.isNotBlank(sessionFile.getFileName());
    }

    private static String resolveStorageKey(AgentExecutionFile sessionFile) {
        if (StringUtils.isNotBlank(sessionFile.getResourceKey())) {
            return sessionFile.getResourceKey();
        }
        if (StringUtils.isNotBlank(sessionFile.getOriginOssUrl())) {
            return sessionFile.getOriginOssUrl();
        }
        if (StringUtils.isNotBlank(sessionFile.getOssUrl())) {
            return sessionFile.getOssUrl();
        }
        if (StringUtils.isNotBlank(sessionFile.getOriginDomainUrl())) {
            return sessionFile.getOriginDomainUrl();
        }
        if (StringUtils.isNotBlank(sessionFile.getDomainUrl())) {
            return sessionFile.getDomainUrl();
        }
        return sessionFile.getFileName();
    }

    private static String buildInputMetadata(AgentExecutionFile sessionFile) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (StringUtils.isNotBlank(sessionFile.getFileDesc())) {
            metadata.put("fileDesc", sessionFile.getFileDesc());
        }
        if (StringUtils.isNotBlank(sessionFile.getFileType())) {
            metadata.put("fileType", sessionFile.getFileType());
        }
        if (StringUtils.isNotBlank(sessionFile.getOriginFileName())) {
            metadata.put("originFileName", sessionFile.getOriginFileName());
        }
        if (StringUtils.isNotBlank(sessionFile.getOriginFileUrl())) {
            metadata.put("originFileUrl", sessionFile.getOriginFileUrl());
        }
        if (metadata.isEmpty()) {
            return null;
        }
        return JSON.toJSONString(metadata);
    }
}
