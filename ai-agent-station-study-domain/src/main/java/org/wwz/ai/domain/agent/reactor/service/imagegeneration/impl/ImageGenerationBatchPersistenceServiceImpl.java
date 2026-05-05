package org.wwz.ai.domain.agent.reactor.service.imagegeneration.impl;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.ImageGenerationExecutionResult;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.WorkspaceImageFile;
import org.wwz.ai.domain.agent.ledger.model.ArtifactRecordCommand;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ImageGenerationToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolFileRef;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolOutputPersistCommand;
import org.wwz.ai.domain.agent.ledger.AgentExecutionRecorder;
import org.wwz.ai.domain.agent.reactor.service.imagegeneration.IImageGenerationBatchPersistenceService;
import org.wwz.ai.domain.agent.reactor.service.imagegeneration.ImageGenerationWorkspaceConstants;
import org.wwz.ai.domain.agent.ledger.tooloutput.ToolOutputWriter;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * 工作台生图批次持久化实现。
 */
@Service
public class ImageGenerationBatchPersistenceServiceImpl implements IImageGenerationBatchPersistenceService {

    @Resource
    private ToolOutputWriter toolOutputWriter;
    @Resource
    private AgentExecutionRecorder agentExecutionRecorder;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void persistWorkspaceBatch(String requestId, ImageGenerationExecutionResult result) {
        if (StringUtils.isBlank(requestId) || result == null || CollectionUtils.isEmpty(result.getFiles())) {
            throw new IllegalArgumentException("工作台生图批次持久化参数不完整");
        }
        String toolCallId = buildWorkspaceToolCallId(requestId);
        toolOutputWriter.writeOrThrow(ToolOutputPersistCommand.builder()
                .requestId(requestId)
                .requestSource(ExecutionLedgerConstants.REQUEST_SOURCE_WORKSPACE)
                .toolCallId(toolCallId)
                .toolName(ImageGenerationWorkspaceConstants.WORKSPACE_TOOL_NAME)
                .status(ExecutionLedgerConstants.STATUS_SUCCESS)
                .structuredOutput(ImageGenerationToolOutput.builder()
                        .prompt(result.getPrompt())
                        .mode(result.getMode())
                        .summary(result.getSummary())
                        .size(result.getSize())
                        .batchCount(result.getBatchCount())
                        .sourceImageCount(result.getSourceImageCount())
                        .maskImageCount(result.getMaskImageCount())
                        .usedFallback(result.getUsedFallback())
                        .fileRefs(toToolFileRefs(result.getFiles()))
                        .build())
                .build());
        agentExecutionRecorder.recordArtifactsOrThrow(buildArtifactRecords(requestId, toolCallId, result.getFiles()));
    }

    private List<ToolFileRef> toToolFileRefs(List<WorkspaceImageFile> files) {
        List<ToolFileRef> fileRefs = new ArrayList<>();
        if (CollectionUtils.isEmpty(files)) {
            return fileRefs;
        }
        for (WorkspaceImageFile file : files) {
            if (file == null) {
                continue;
            }
            fileRefs.add(ToolFileRef.builder()
                    .fileName(file.getFileName())
                    .downloadUrl(file.getDownloadUrl())
                    .previewUrl(file.getPreviewUrl())
                    .ossUrl(file.getOssUrl())
                    .domainUrl(file.getDomainUrl())
                    .fileSize(file.getFileSize())
                    .mimeType(file.getMimeType())
                    .build());
        }
        return fileRefs;
    }

    private List<ArtifactRecordCommand> buildArtifactRecords(String requestId,
                                                             String toolCallId,
                                                             List<WorkspaceImageFile> files) {
        List<ArtifactRecordCommand> records = new ArrayList<>();
        if (CollectionUtils.isEmpty(files)) {
            return records;
        }
        for (WorkspaceImageFile file : files) {
            if (file == null || StringUtils.isBlank(file.getFileName())) {
                continue;
            }
            records.add(ArtifactRecordCommand.builder()
                    .requestId(requestId)
                    .toolCallId(toolCallId)
                    .artifactRole(ExecutionLedgerConstants.ARTIFACT_ROLE_OUTPUT)
                    .visibility(ExecutionLedgerConstants.VISIBILITY_VISIBLE)
                    .sourceType(ExecutionLedgerConstants.SOURCE_TYPE_TOOL_OUTPUT)
                    .sourceName(ImageGenerationWorkspaceConstants.WORKSPACE_TOOL_NAME)
                    .fileName(file.getFileName())
                    .storageKey(resolveStorageKey(file))
                    .downloadUrl(file.getDownloadUrl())
                    .previewUrl(file.getPreviewUrl())
                    .mimeType(file.getMimeType())
                    .fileSize(file.getFileSize())
                    .build());
        }
        return records;
    }

    private String resolveStorageKey(WorkspaceImageFile file) {
        return StringUtils.firstNonBlank(file.getDownloadUrl(), file.getOssUrl(), file.getPreviewUrl(), file.getDomainUrl(), file.getFileName());
    }

    public static String buildWorkspaceToolCallId(String requestId) {
        return ImageGenerationWorkspaceConstants.WORKSPACE_TOOL_CALL_ID_PREFIX + requestId;
    }
}
