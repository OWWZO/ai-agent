package org.wwz.ai.trigger.http.agent.image.mapper;

import org.wwz.ai.application.agent.image.command.WorkspaceImageGenerationCommand;
import org.wwz.ai.application.agent.image.command.WorkspaceImageHistoryQuery;
import org.wwz.ai.application.agent.image.result.WorkspaceImageFileView;
import org.wwz.ai.application.agent.image.result.WorkspaceImageGenerationView;
import org.wwz.ai.application.agent.image.result.WorkspaceImageHistoryBatchView;
import org.wwz.ai.trigger.http.agent.image.vo.WorkspaceImageFileRespVO;
import org.wwz.ai.trigger.http.agent.image.vo.WorkspaceImageGenerationReqVO;
import org.wwz.ai.trigger.http.agent.image.vo.WorkspaceImageGenerationRespVO;
import org.wwz.ai.trigger.http.agent.image.vo.WorkspaceImageHistoryBatchRespVO;

import java.util.List;

/**
 * 生图 HTTP VO 与用例模型互转。
 * <p>Trigger 只负责协议适配：请求 VO -> 用例命令，用例输出 -> 响应 VO。</p>
 */
public final class WorkspaceImageVoMapper {

    private WorkspaceImageVoMapper() {
    }

    public static WorkspaceImageGenerationCommand toCommand(WorkspaceImageGenerationReqVO reqVO) {
        if (reqVO == null) {
            return null;
        }
        return WorkspaceImageGenerationCommand.builder()
                .requestId(reqVO.getRequestId())
                .prompt(reqVO.getPrompt())
                .mode(reqVO.getMode())
                .fileNames(reqVO.getFileNames())
                .maskFileNames(reqVO.getMaskFileNames())
                .fileName(reqVO.getFileName())
                .fileDescription(reqVO.getFileDescription())
                .size(reqVO.getSize())
                .n(reqVO.getN())
                .build();
    }

    public static WorkspaceImageHistoryQuery toQuery(int pageNo, int pageSize) {
        return WorkspaceImageHistoryQuery.builder()
                .pageNo(pageNo)
                .pageSize(pageSize)
                .build();
    }

    public static WorkspaceImageGenerationRespVO toRespVO(WorkspaceImageGenerationView view) {
        if (view == null) {
            return null;
        }
        return WorkspaceImageGenerationRespVO.builder()
                .data(view.getData())
                .fileInfo(toFileRespList(view.getFileInfo()))
                .requestId(view.getRequestId())
                .mode(view.getMode())
                .usedFallback(view.getUsedFallback())
                .rawResponse(view.getRawResponse())
                .build();
    }

    public static WorkspaceImageHistoryBatchRespVO toHistoryRespVO(WorkspaceImageHistoryBatchView batch) {
        if (batch == null) {
            return null;
        }
        return WorkspaceImageHistoryBatchRespVO.builder()
                .requestId(batch.getRequestId())
                .prompt(batch.getPrompt())
                .mode(batch.getMode())
                .size(batch.getSize())
                .batchCount(batch.getBatchCount())
                .sourceImageCount(batch.getSourceImageCount())
                .maskImageCount(batch.getMaskImageCount())
                .usedFallback(batch.getUsedFallback())
                .createdAt(batch.getCreatedAt())
                .images(toFileRespList(batch.getImages()))
                .build();
    }

    public static List<WorkspaceImageFileRespVO> toFileRespList(List<WorkspaceImageFileView> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        return files.stream()
                .filter(file -> file != null)
                .map(WorkspaceImageVoMapper::toFileRespVO)
                .toList();
    }

    private static WorkspaceImageFileRespVO toFileRespVO(WorkspaceImageFileView file) {
        return WorkspaceImageFileRespVO.builder()
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
