package org.wwz.ai.domain.agent.ledger;

import com.alibaba.fastjson.JSON;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.reactor.model.dto.FileInformation;
import org.wwz.ai.domain.agent.ledger.model.ArtifactRecordCommand;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunFinishRecord;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunStartRecord;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;

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
                                     AgentRequest request,
                                     String entryAgent) {
        if (recorder == null || agentContext == null || request == null) {
            return;
        }
        Long runId = recorder.createRun(DialogueRunStartRecord.builder()
                .runUid(request.getRequestId())
                .requestId(request.getRequestId())
                .sessionId(request.getSessionId())
                .visitorId(request.getVisitorId())
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

    private static void recordInputArtifacts(AgentExecutionRecorder recorder,
                                             List<FileInformation> sessionFiles,
                                             Long runId,
                                             String requestId) {
        List<ArtifactRecordCommand> inputArtifacts = buildInputArtifacts(sessionFiles, runId, requestId);
        if (!inputArtifacts.isEmpty()) {
            recorder.recordArtifacts(inputArtifacts);
        }
    }

    private static List<ArtifactRecordCommand> buildInputArtifacts(List<FileInformation> sessionFiles,
                                                                   Long runId,
                                                                   String requestId) {
        if (runId == null || sessionFiles == null || sessionFiles.isEmpty()) {
            return List.of();
        }
        List<ArtifactRecordCommand> records = new ArrayList<>(sessionFiles.size());
        for (FileInformation sessionFile : sessionFiles) {
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

    private static boolean hasValidFileName(FileInformation sessionFile) {
        return sessionFile != null && StringUtils.isNotBlank(sessionFile.getFileName());
    }

    private static String resolveStorageKey(FileInformation sessionFile) {
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

    private static String buildInputMetadata(FileInformation sessionFile) {
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
