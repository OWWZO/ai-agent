package org.wwz.ai.domain.agent.reactor.service.imagegeneration.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.wwz.ai.domain.agent.runtime.util.StringUtil;
import org.wwz.ai.domain.agent.reactor.gateway.IReactorImageGenerationGateway;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.ImageGenerationExecuteCommand;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.ImageGenerationExecutionResult;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.ImageGenerationGatewayFile;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.ImageGenerationGatewayRequest;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.ImageGenerationGatewayResponse;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.WorkspaceImageFile;
import org.wwz.ai.domain.agent.reactor.service.imagegeneration.IImageGenerationExecutionKernel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 生图执行内核实现，统一请求归一化与结果归一化。
 */
@Service
@RequiredArgsConstructor
public class ImageGenerationExecutionKernelImpl implements IImageGenerationExecutionKernel {

    private static final String DEFAULT_IMAGE_SIZE = "1024x1024";
    private static final int DEFAULT_TIMEOUT_SECONDS = 900;
    private static final int DEFAULT_BATCH_SIZE = 1;

    private final IReactorImageGenerationGateway imageGenerationGateway;

    @Override
    public ImageGenerationExecutionResult execute(ImageGenerationExecuteCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("生图执行命令不能为空");
        }
        if (StringUtils.isBlank(command.getPrompt())) {
            throw new IllegalArgumentException("prompt不能为空");
        }

        ImageGenerationGatewayRequest request = ImageGenerationGatewayRequest.builder()
                .requestId(command.getRequestId())
                .prompt(StringUtils.trim(command.getPrompt()))
                .mode(StringUtils.trimToNull(command.getMode()))
                .fileNames(defaultList(command.getFileNames()))
                .maskFileNames(defaultList(command.getMaskFileNames()))
                .fileName(StringUtils.trimToNull(command.getFileName()))
                .fileDescription(StringUtils.trimToNull(command.getFileDescription()))
                .size(StringUtils.defaultIfBlank(StringUtils.trim(command.getSize()), DEFAULT_IMAGE_SIZE))
                .n(command.getN() == null ? DEFAULT_BATCH_SIZE : command.getN())
                .timeoutSeconds(command.getTimeoutSeconds() == null ? DEFAULT_TIMEOUT_SECONDS : command.getTimeoutSeconds())
                .model(StringUtils.trimToNull(command.getModel()))
                .stream(Boolean.FALSE)
                .build();

        ImageGenerationGatewayResponse response = imageGenerationGateway.generate(request);
        List<WorkspaceImageFile> files = normalizeFiles(response == null ? null : response.getFileInfo());

        return ImageGenerationExecutionResult.builder()
                .requestId(request.getRequestId())
                .prompt(request.getPrompt())
                .mode(StringUtils.defaultIfBlank(response == null ? null : response.getMode(), request.getMode()))
                .summary(StringUtils.defaultIfBlank(response == null ? null : response.getData(), "生成完成"))
                .size(request.getSize())
                .batchCount(request.getN())
                .sourceImageCount(request.getFileNames().size())
                .maskImageCount((int) request.getMaskFileNames().stream().filter(StringUtils::isNotBlank).count())
                .usedFallback(Boolean.TRUE.equals(response == null ? null : response.getUsedFallback()))
                .rawResponse(response == null ? null : response.getRawResponse())
                .files(files)
                .build();
    }

    private List<String> defaultList(List<String> values) {
        if (CollectionUtils.isEmpty(values)) {
            return Collections.emptyList();
        }
        List<String> normalized = new ArrayList<>(values.size());
        for (String value : values) {
            normalized.add(value == null ? "" : value.trim());
        }
        return normalized;
    }

    private List<WorkspaceImageFile> normalizeFiles(List<ImageGenerationGatewayFile> fileInfoList) {
        if (CollectionUtils.isEmpty(fileInfoList)) {
            return Collections.emptyList();
        }
        List<WorkspaceImageFile> files = new ArrayList<>(fileInfoList.size());
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
}
