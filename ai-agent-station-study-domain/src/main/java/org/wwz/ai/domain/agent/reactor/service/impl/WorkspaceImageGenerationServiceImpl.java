package org.wwz.ai.domain.agent.reactor.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.wwz.ai.domain.agent.reactor.agent.util.StringUtil;
import org.wwz.ai.domain.agent.reactor.entity.AgentImageGenerationRecord;
import org.wwz.ai.domain.agent.reactor.gateway.IReactorImageGenerationGateway;
import org.wwz.ai.domain.agent.reactor.mapper.IAgentImageGenerationRecordDao;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.ImageGenerationGatewayFile;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.ImageGenerationGatewayRequest;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.ImageGenerationGatewayResponse;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.WorkspaceImageFile;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.WorkspaceImageGenerationCommand;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.WorkspaceImageGenerationHistoryBatch;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.WorkspaceImageGenerationHistoryPage;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.WorkspaceImageGenerationResult;
import org.wwz.ai.domain.agent.reactor.service.IWorkspaceImageGenerationService;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 生图工作台服务实现。
 */
@Slf4j
@Service
public class WorkspaceImageGenerationServiceImpl implements IWorkspaceImageGenerationService {

    private static final String MODE_IMAGES = "images";
    private static final String MODE_EDITS = "edits";
    private static final String DEFAULT_OUTPUT_NAME = "图片生成结果";
    private static final String DEFAULT_IMAGE_SIZE = "1024x1024";
    private static final int DEFAULT_BATCH_SIZE = 10;
    private static final int MAX_BATCH_SIZE = 50;

    @Resource
    private IReactorImageGenerationGateway imageGenerationGateway;
    @Resource
    private IAgentImageGenerationRecordDao imageGenerationRecordDao;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkspaceImageGenerationResult generate(String deviceId, WorkspaceImageGenerationCommand command) {
        validateGenerateRequest(deviceId, command);

        List<String> sourceImages = normalizeSourceImages(command.getFileNames());
        List<String> maskImages = normalizeMaskImages(command.getMaskFileNames());
        String mode = resolveMode(command.getMode(), sourceImages);
        if (MODE_EDITS.equals(mode) && sourceImages.isEmpty()) {
            throw new IllegalArgumentException("图生图模式至少需要一张参考图片");
        }
        if (maskImages.size() > sourceImages.size()) {
            throw new IllegalArgumentException("maskFileNames 数量不能超过 fileNames");
        }

        String requestId = StringUtil.firstNonBlank(command.getRequestId(), StringUtil.getUUID());
        int batchSize = normalizeBatchSize(command.getN());
        ImageGenerationGatewayRequest gatewayRequest = ImageGenerationGatewayRequest.builder()
                .requestId(requestId)
                .prompt(command.getPrompt().trim())
                .mode(mode)
                .fileNames(sourceImages)
                .maskFileNames(maskImages)
                .fileName(resolveOutputFileName(command.getFileName()))
                .fileDescription(resolveFileDescription(command.getFileDescription(), command.getPrompt()))
                .size(resolveSize(command.getSize()))
                .n(batchSize)
                .timeoutSeconds(300)
                .stream(Boolean.FALSE)
                .build();

        ImageGenerationGatewayResponse gatewayResponse = imageGenerationGateway.generate(gatewayRequest);
        List<WorkspaceImageFile> files = normalizeWorkspaceFiles(gatewayResponse.getFileInfo());
        if (files.isEmpty()) {
            throw new IllegalStateException("上游未返回可识别的图片结果");
        }

        persistRecords(deviceId, gatewayRequest, gatewayResponse, files);
        log.info("生图工作台生成成功 requestId={}, deviceId={}, fileCount={}",
                requestId, deviceId, files.size());

        return WorkspaceImageGenerationResult.builder()
                .data(StringUtil.firstNonBlank(gatewayResponse.getData(), "生成完成"))
                .fileInfo(files)
                .requestId(requestId)
                .mode(mode)
                .usedFallback(Boolean.TRUE.equals(gatewayResponse.getUsedFallback()))
                .rawResponse(gatewayResponse.getRawResponse())
                .build();
    }

    @Override
    public WorkspaceImageGenerationHistoryPage queryHistory(String deviceId, int pageNo, int pageSize) {
        if (!org.springframework.util.StringUtils.hasText(deviceId)) {
            throw new IllegalArgumentException("X-Device-Id header is required");
        }

        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.min(pageSize > 0 ? pageSize : DEFAULT_BATCH_SIZE, MAX_BATCH_SIZE);
        int total = imageGenerationRecordDao.countDistinctRequestIdByDeviceId(deviceId);
        if (total <= 0) {
            return WorkspaceImageGenerationHistoryPage.builder()
                    .total(0)
                    .list(Collections.emptyList())
                    .build();
        }

        int offset = (safePageNo - 1) * safePageSize;
        List<String> requestIds = imageGenerationRecordDao.queryRequestIdsByDeviceId(deviceId, offset, safePageSize);
        if (CollectionUtils.isEmpty(requestIds)) {
            return WorkspaceImageGenerationHistoryPage.builder()
                    .total(total)
                    .list(Collections.emptyList())
                    .build();
        }

        List<AgentImageGenerationRecord> records = imageGenerationRecordDao.queryByRequestIds(deviceId, requestIds);
        Map<String, WorkspaceImageGenerationHistoryBatch> batchMap = new LinkedHashMap<>();
        for (String requestId : requestIds) {
            batchMap.put(requestId, WorkspaceImageGenerationHistoryBatch.builder()
                    .requestId(requestId)
                    .images(new ArrayList<>())
                    .build());
        }

        for (AgentImageGenerationRecord record : records) {
            WorkspaceImageGenerationHistoryBatch batch = batchMap.get(record.getRequestId());
            if (batch == null) {
                continue;
            }
            if (!org.springframework.util.StringUtils.hasText(batch.getPrompt())) {
                batch.setPrompt(record.getPrompt());
                batch.setMode(record.getMode());
                batch.setSize(record.getSize());
                batch.setBatchCount(record.getBatchCount());
                batch.setSourceImageCount(record.getSourceImageCount());
                batch.setMaskImageCount(record.getMaskImageCount());
                batch.setUsedFallback(record.getUsedFallback() != null && record.getUsedFallback() == 1);
                batch.setCreatedAt(record.getCreateTime());
            }
            batch.getImages().add(toWorkspaceFile(record));
        }

        return WorkspaceImageGenerationHistoryPage.builder()
                .total(total)
                .list(new ArrayList<>(batchMap.values()))
                .build();
    }

    private void validateGenerateRequest(String deviceId, WorkspaceImageGenerationCommand command) {
        if (!org.springframework.util.StringUtils.hasText(deviceId)) {
            throw new IllegalArgumentException("X-Device-Id header is required");
        }
        if (command == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (!org.springframework.util.StringUtils.hasText(command.getPrompt())) {
            throw new IllegalArgumentException("prompt不能为空");
        }
    }

    private List<String> normalizeSourceImages(List<String> fileNames) {
        if (CollectionUtils.isEmpty(fileNames)) {
            return Collections.emptyList();
        }
        return fileNames.stream()
                .filter(org.springframework.util.StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * 蒙版列表需要保留空占位，避免打乱和参考图的顺序关系。
     */
    private List<String> normalizeMaskImages(List<String> maskFileNames) {
        if (maskFileNames == null) {
            return Collections.emptyList();
        }
        List<String> normalized = new ArrayList<>(maskFileNames.size());
        for (String maskFileName : maskFileNames) {
            normalized.add(maskFileName == null ? "" : maskFileName.trim());
        }
        return normalized;
    }

    private String resolveMode(String rawMode, List<String> sourceImages) {
        String mode = StringUtils.trimToEmpty(rawMode);
        if (MODE_IMAGES.equals(mode) || MODE_EDITS.equals(mode)) {
            return mode;
        }
        return sourceImages.isEmpty() ? MODE_IMAGES : MODE_EDITS;
    }

    private int normalizeBatchSize(Integer rawBatchSize) {
        if (rawBatchSize == null) {
            return 1;
        }
        return Math.max(1, Math.min(rawBatchSize, 10));
    }

    private String resolveOutputFileName(String rawFileName) {
        String fileName = StringUtil.removeSpecialChars(StringUtils.trimToEmpty(rawFileName));
        return org.springframework.util.StringUtils.hasText(fileName) ? fileName : DEFAULT_OUTPUT_NAME;
    }

    private String resolveFileDescription(String rawFileDescription, String prompt) {
        String fileDescription = StringUtils.trimToEmpty(rawFileDescription);
        if (org.springframework.util.StringUtils.hasText(fileDescription)) {
            return fileDescription;
        }
        return StringUtil.abbreviate(prompt, 80, true);
    }

    private String resolveSize(String rawSize) {
        String size = StringUtils.trimToEmpty(rawSize);
        return org.springframework.util.StringUtils.hasText(size) ? size : DEFAULT_IMAGE_SIZE;
    }

    private List<WorkspaceImageFile> normalizeWorkspaceFiles(List<ImageGenerationGatewayFile> fileInfoList) {
        if (CollectionUtils.isEmpty(fileInfoList)) {
            return Collections.emptyList();
        }

        List<WorkspaceImageFile> files = new ArrayList<>();
        for (ImageGenerationGatewayFile fileInfo : fileInfoList) {
            if (fileInfo == null) {
                continue;
            }
            files.add(WorkspaceImageFile.builder()
                    .fileName(fileInfo.getFileName())
                    .ossUrl(fileInfo.getOssUrl())
                    .domainUrl(fileInfo.getDomainUrl())
                    .downloadUrl(StringUtil.firstNonBlank(fileInfo.getDownloadUrl(), fileInfo.getOssUrl(), fileInfo.getDomainUrl()))
                    .previewUrl(StringUtil.firstNonBlank(fileInfo.getPreviewUrl(), fileInfo.getDomainUrl(), fileInfo.getDownloadUrl(), fileInfo.getOssUrl()))
                    .fileSize(fileInfo.getFileSize())
                    .mimeType(fileInfo.getMimeType())
                    .build());
        }
        return files;
    }

    private void persistRecords(String deviceId,
                                ImageGenerationGatewayRequest gatewayRequest,
                                ImageGenerationGatewayResponse gatewayResponse,
                                List<WorkspaceImageFile> files) {
        int maskImageCount = (int) gatewayRequest.getMaskFileNames().stream()
                .filter(org.springframework.util.StringUtils::hasText)
                .count();

        for (int index = 0; index < files.size(); index++) {
            WorkspaceImageFile file = files.get(index);
            AgentImageGenerationRecord record = AgentImageGenerationRecord.builder()
                    .requestId(gatewayRequest.getRequestId())
                    .resultIndex(index)
                    .deviceId(deviceId)
                    .userId(null)
                    .prompt(gatewayRequest.getPrompt())
                    .mode(gatewayRequest.getMode())
                    .size(gatewayRequest.getSize())
                    .batchCount(files.size())
                    .sourceImageCount(gatewayRequest.getFileNames().size())
                    .maskImageCount(maskImageCount)
                    .usedFallback(Boolean.TRUE.equals(gatewayResponse.getUsedFallback()) ? 1 : 0)
                    .fileName(file.getFileName())
                    .ossUrl(file.getOssUrl())
                    .domainUrl(file.getDomainUrl())
                    .downloadUrl(file.getDownloadUrl())
                    .fileSize(file.getFileSize())
                    .mimeType(file.getMimeType())
                    .deleted(0)
                    .build();
            imageGenerationRecordDao.insert(record);
        }
    }

    private WorkspaceImageFile toWorkspaceFile(AgentImageGenerationRecord record) {
        return WorkspaceImageFile.builder()
                .fileName(record.getFileName())
                .ossUrl(record.getOssUrl())
                .domainUrl(record.getDomainUrl())
                .downloadUrl(StringUtil.firstNonBlank(record.getDownloadUrl(), record.getOssUrl(), record.getDomainUrl()))
                .previewUrl(StringUtil.firstNonBlank(record.getDomainUrl(), record.getDownloadUrl(), record.getOssUrl()))
                .fileSize(record.getFileSize())
                .mimeType(record.getMimeType())
                .build();
    }
}
