package org.wwz.ai.application.agent.image.mapper;

import org.wwz.ai.application.agent.image.command.WorkspaceImageGenerationCommand;
import org.wwz.ai.application.agent.image.result.WorkspaceImageFileView;
import org.wwz.ai.application.agent.image.result.WorkspaceImageGenerationView;
import org.wwz.ai.application.agent.image.result.WorkspaceImageHistoryBatchView;
import org.wwz.ai.application.agent.image.result.WorkspaceImageHistoryPageView;
import org.wwz.ai.domain.agent.image.model.WorkspaceImageFile;
import org.wwz.ai.domain.agent.image.model.WorkspaceImageGenerationHistoryBatch;
import org.wwz.ai.domain.agent.image.model.WorkspaceImageGenerationHistoryPage;
import org.wwz.ai.domain.agent.image.model.WorkspaceImageGenerationResult;

import java.util.List;

/**
 * 生图领域模型与用例模型互转。
 * <p>应用层负责领域结果到用例输出的映射，Trigger 只消费用例模型。</p>
 */
public final class WorkspaceImageMapper {

    private WorkspaceImageMapper() {
    }

    public static org.wwz.ai.domain.agent.image.model.WorkspaceImageGenerationCommand toDomainCommand(
            WorkspaceImageGenerationCommand command) {
        if (command == null) {
            return null;
        }
        return org.wwz.ai.domain.agent.image.model.WorkspaceImageGenerationCommand.builder()
                .requestId(command.getRequestId())
                .prompt(command.getPrompt())
                .mode(command.getMode())
                .fileNames(command.getFileNames())
                .maskFileNames(command.getMaskFileNames())
                .fileName(command.getFileName())
                .fileDescription(command.getFileDescription())
                .size(command.getSize())
                .n(command.getN())
                .build();
    }

    public static WorkspaceImageGenerationView toGenerationView(WorkspaceImageGenerationResult result) {
        if (result == null) {
            return null;
        }
        return WorkspaceImageGenerationView.builder()
                .data(result.getData())
                .fileInfo(toFileViews(result.getFileInfo()))
                .requestId(result.getRequestId())
                .mode(result.getMode())
                .usedFallback(result.getUsedFallback())
                .rawResponse(result.getRawResponse())
                .build();
    }

    public static WorkspaceImageHistoryPageView toHistoryPageView(WorkspaceImageGenerationHistoryPage page) {
        if (page == null) {
            return WorkspaceImageHistoryPageView.builder()
                    .total(0)
                    .list(List.of())
                    .build();
        }
        List<WorkspaceImageHistoryBatchView> batches = page.getList() == null
                ? List.of()
                : page.getList().stream().map(WorkspaceImageMapper::toHistoryBatchView).toList();
        return WorkspaceImageHistoryPageView.builder()
                .total(page.getTotal())
                .list(batches)
                .build();
    }

    public static WorkspaceImageHistoryBatchView toHistoryBatchView(WorkspaceImageGenerationHistoryBatch batch) {
        if (batch == null) {
            return null;
        }
        return WorkspaceImageHistoryBatchView.builder()
                .requestId(batch.getRequestId())
                .prompt(batch.getPrompt())
                .mode(batch.getMode())
                .size(batch.getSize())
                .batchCount(batch.getBatchCount())
                .sourceImageCount(batch.getSourceImageCount())
                .maskImageCount(batch.getMaskImageCount())
                .usedFallback(batch.getUsedFallback())
                .createdAt(batch.getCreatedAt())
                .images(toFileViews(batch.getImages()))
                .build();
    }

    public static List<WorkspaceImageFileView> toFileViews(List<WorkspaceImageFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        return files.stream()
                .filter(file -> file != null)
                .map(WorkspaceImageMapper::toFileView)
                .toList();
    }

    public static WorkspaceImageFileView toFileView(WorkspaceImageFile file) {
        if (file == null) {
            return null;
        }
        return WorkspaceImageFileView.builder()
                .fileName(file.getFileName())
                .ossUrl(file.getOssUrl())
                .domainUrl(file.getDomainUrl())
                .downloadUrl(file.getDownloadUrl())
                .previewUrl(file.getPreviewUrl())
                .fileSize(file.getFileSize())
                .mimeType(file.getMimeType())
                .build();
    }
}
