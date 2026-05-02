package org.wwz.ai.trigger.http.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.WorkspaceImageFile;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.WorkspaceImageGenerationCommand;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.WorkspaceImageGenerationHistoryBatch;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.WorkspaceImageGenerationHistoryPage;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.WorkspaceImageGenerationResult;
import org.wwz.ai.domain.agent.reactor.service.IWorkspaceImageGenerationService;
import org.wwz.ai.trigger.http.agent.vo.PageRespVO;
import org.wwz.ai.trigger.http.agent.vo.WorkspaceImageFileRespVO;
import org.wwz.ai.trigger.http.agent.vo.WorkspaceImageGenerationReqVO;
import org.wwz.ai.trigger.http.agent.vo.WorkspaceImageGenerationRespVO;
import org.wwz.ai.trigger.http.agent.vo.WorkspaceImageHistoryBatchRespVO;
import org.wwz.ai.types.enums.ResponseCode;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 生图工作台接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/agent/image-generation")
public class AgentImageGenerationController {

    @Resource
    private IWorkspaceImageGenerationService workspaceImageGenerationService;

    @PostMapping("/generate")
    public Response<WorkspaceImageGenerationRespVO> generate(@RequestBody WorkspaceImageGenerationReqVO reqVO) {
        try {
            if (reqVO == null) {
                throw new IllegalArgumentException("请求体不能为空");
            }
            WorkspaceImageGenerationResult result = workspaceImageGenerationService.generate(
                    WorkspaceImageGenerationCommand.builder()
                            .requestId(reqVO.getRequestId())
                            .prompt(reqVO.getPrompt())
                            .mode(reqVO.getMode())
                            .fileNames(reqVO.getFileNames())
                            .maskFileNames(reqVO.getMaskFileNames())
                            .fileName(reqVO.getFileName())
                            .fileDescription(reqVO.getFileDescription())
                            .model(reqVO.getModel())
                            .size(reqVO.getSize())
                            .n(reqVO.getN())
                            .build()
            );

            WorkspaceImageGenerationRespVO respVO = WorkspaceImageGenerationRespVO.builder()
                    .data(result.getData())
                    .fileInfo(toFileRespList(result.getFileInfo()))
                    .requestId(result.getRequestId())
                    .mode(result.getMode())
                    .usedFallback(result.getUsedFallback())
                    .rawResponse(result.getRawResponse())
                    .build();
            return Response.<WorkspaceImageGenerationRespVO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(respVO)
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.<WorkspaceImageGenerationRespVO>builder()
                    .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                    .info(e.getMessage())
                    .build();
        } catch (Exception e) {
            log.error("生图工作台生成失败", e);
            return Response.<WorkspaceImageGenerationRespVO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(e.getMessage())
                    .build();
        }
    }

    @GetMapping("/history")
    public Response<PageRespVO<WorkspaceImageHistoryBatchRespVO>> history(@RequestParam(name = "pageNo", defaultValue = "1") int pageNo,
                                                                          @RequestParam(name = "pageSize", defaultValue = "10") int pageSize) {
        try {
            WorkspaceImageGenerationHistoryPage historyPage =
                    workspaceImageGenerationService.queryHistory(pageNo, pageSize);

            List<WorkspaceImageHistoryBatchRespVO> list = historyPage.getList().stream()
                    .map(this::toHistoryRespVO)
                    .collect(Collectors.toList());
            return Response.<PageRespVO<WorkspaceImageHistoryBatchRespVO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(PageRespVO.<WorkspaceImageHistoryBatchRespVO>builder()
                            .total(historyPage.getTotal())
                            .list(list)
                            .build())
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.<PageRespVO<WorkspaceImageHistoryBatchRespVO>>builder()
                    .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                    .info(e.getMessage())
                    .build();
        } catch (Exception e) {
            log.error("查询生图历史失败", e);
            return Response.<PageRespVO<WorkspaceImageHistoryBatchRespVO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(e.getMessage())
                    .build();
        }
    }

    private WorkspaceImageHistoryBatchRespVO toHistoryRespVO(WorkspaceImageGenerationHistoryBatch batch) {
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

    private List<WorkspaceImageFileRespVO> toFileRespList(List<WorkspaceImageFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        return files.stream()
                .map(file -> WorkspaceImageFileRespVO.builder()
                        .fileName(file.getFileName())
                        .ossUrl(file.getOssUrl())
                        .domainUrl(file.getDomainUrl())
                        .downloadUrl(file.getDownloadUrl())
                        .previewUrl(file.getPreviewUrl())
                        .fileSize(file.getFileSize())
                        .mimeType(file.getMimeType())
                        .build())
                .collect(Collectors.toList());
    }
}
