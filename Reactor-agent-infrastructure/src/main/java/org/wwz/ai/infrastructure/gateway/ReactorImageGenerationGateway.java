package org.wwz.ai.infrastructure.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.wwz.ai.domain.agent.runtime.util.StringUtil;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.reactor.gateway.IReactorImageGenerationGateway;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.ImageGenerationGatewayFile;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.ImageGenerationGatewayRequest;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.ImageGenerationGatewayResponse;
import org.wwz.ai.infrastructure.gateway.dto.ConversationUploadFileDTO;
import org.wwz.ai.infrastructure.imagegeneration.MicuImageGenerationClient;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Java 端图片生成网关：直连米醋 / OpenAI 兼容生图接口，产物上传文件服务。
 */
@Slf4j
@Component
public class ReactorImageGenerationGateway implements IReactorImageGenerationGateway {

    private static final Pattern UNSAFE_FILE_CHARS = Pattern.compile("[<>:\"/\\\\|?*\\x00-\\x1F]+");

    @Resource
    private ReactorConfig reactorConfig;

    @Resource
    private ReactorFileGateway reactorFileGateway;

    @Override
    public ImageGenerationGatewayResponse generate(ImageGenerationGatewayRequest requestDTO) {
        if (requestDTO == null) {
            throw new IllegalArgumentException("生图请求不能为空");
        }
        if (!StringUtils.hasText(requestDTO.getPrompt())) {
            throw new IllegalArgumentException("prompt 不能为空");
        }

        MicuImageGenerationClient client = buildClient(requestDTO.getTimeoutSeconds());
        MicuImageGenerationClient.GenerationResult result = client.generate(
                MicuImageGenerationClient.GenerationRequest.builder()
                        .requestId(requestDTO.getRequestId())
                        .prompt(requestDTO.getPrompt())
                        .mode(requestDTO.getMode())
                        .fileNames(requestDTO.getFileNames())
                        .maskFileNames(requestDTO.getMaskFileNames())
                        .model(requestDTO.getModel())
                        .size(requestDTO.getSize())
                        .n(requestDTO.getN())
                        .build()
        );

        List<ImageGenerationGatewayFile> files = uploadGeneratedImages(
                requestDTO.getRequestId(),
                requestDTO.getFileName(),
                client,
                result.getImages()
        );
        if (files.isEmpty()) {
            throw new IllegalStateException("图片生成成功，但上传产物失败");
        }

        String summary = buildSummary(result.getMode(), files, result.isUsedFallback());
        return ImageGenerationGatewayResponse.builder()
                .requestId(requestDTO.getRequestId())
                .mode(result.getMode())
                .data(summary)
                .usedFallback(result.isUsedFallback())
                .fileInfo(normalizeFileInfo(files))
                .rawResponse(Map.of(
                        "model", result.getModel(),
                        "size", result.getSize(),
                        "notes", result.getNotes() == null ? List.of() : result.getNotes(),
                        "imageCount", files.size()
                ))
                .build();
    }

    private MicuImageGenerationClient buildClient(Integer timeoutSeconds) {
        String baseUrl = firstText(
                reactorConfig.getImageGenerationBaseUrl(),
                reactorConfig.getImageGenerationUrl()
        );
        String apiKey = reactorConfig.getImageGenerationApiKey();
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException("autobots.autoagent.image_generation.base_url 未配置");
        }
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("autobots.autoagent.image_generation.api_key 未配置");
        }
        long timeout = timeoutSeconds == null || timeoutSeconds <= 0 ? 900L : timeoutSeconds.longValue();
        return new MicuImageGenerationClient(MicuImageGenerationClient.ClientConfig.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .defaultModel(firstText(reactorConfig.getImageGenerationModel(), "gpt-image-2"))
                .grokBaseUrl(firstText(reactorConfig.getImageGenerationGrokBaseUrl(), baseUrl))
                .grokApiKey(firstText(reactorConfig.getImageGenerationGrokApiKey(), apiKey))
                .defaultGrokModel(firstText(reactorConfig.getImageGenerationGrokModel(), "grok-imagine-image-lite"))
                .previewBaseUrl(reactorConfig.getCodeInterpreterUrl())
                .timeoutSeconds(timeout)
                .build());
    }

    private List<ImageGenerationGatewayFile> uploadGeneratedImages(String requestId,
                                                                   String rawFileName,
                                                                   MicuImageGenerationClient client,
                                                                   List<MicuImageGenerationClient.GeneratedImage> images) {
        if (images == null || images.isEmpty()) {
            return Collections.emptyList();
        }
        String baseName = sanitizeOutputName(rawFileName);
        List<ImageGenerationGatewayFile> files = new ArrayList<>(images.size());
        for (int i = 0; i < images.size(); i++) {
            MicuImageGenerationClient.GeneratedImage image = images.get(i);
            byte[] bytes = client.materialize(image);
            String mimeType = client.guessMime(bytes);
            String extension = extensionFromMime(mimeType);
            String fileName = buildOutputFileName(baseName, i, images.size(), extension);
            ConversationUploadFileDTO uploaded = reactorFileGateway.uploadBinaryFile(
                    StringUtils.hasText(requestId) ? requestId : "image-generation",
                    fileName,
                    bytes,
                    mimeType
            );
            files.add(ImageGenerationGatewayFile.builder()
                    .fileName(uploaded.getName())
                    .ossUrl(firstText(uploaded.getDownloadUrl(), uploaded.getUrl()))
                    .domainUrl(firstText(uploaded.getPreviewUrl(), uploaded.getUrl(), uploaded.getDownloadUrl()))
                    .downloadUrl(firstText(uploaded.getDownloadUrl(), uploaded.getUrl()))
                    .previewUrl(firstText(uploaded.getPreviewUrl(), uploaded.getUrl(), uploaded.getDownloadUrl()))
                    .fileSize(uploaded.getSize())
                    .mimeType(firstText(uploaded.getMimeType(), mimeType))
                    .build());
        }
        return files;
    }

    private List<ImageGenerationGatewayFile> normalizeFileInfo(List<ImageGenerationGatewayFile> fileInfoList) {
        if (fileInfoList == null || fileInfoList.isEmpty()) {
            return Collections.emptyList();
        }
        for (ImageGenerationGatewayFile fileInfo : fileInfoList) {
            if (fileInfo == null) {
                continue;
            }
            String downloadUrl = StringUtil.firstNonBlank(
                    fileInfo.getDownloadUrl(),
                    fileInfo.getOssUrl(),
                    fileInfo.getDomainUrl()
            );
            String previewUrl = StringUtil.firstNonBlank(
                    fileInfo.getPreviewUrl(),
                    fileInfo.getDomainUrl(),
                    downloadUrl,
                    fileInfo.getOssUrl()
            );
            fileInfo.setDownloadUrl(downloadUrl);
            fileInfo.setPreviewUrl(previewUrl);
            if (!StringUtils.hasText(fileInfo.getOssUrl())) {
                fileInfo.setOssUrl(downloadUrl);
            }
            if (!StringUtils.hasText(fileInfo.getDomainUrl())) {
                fileInfo.setDomainUrl(previewUrl);
            }
        }
        return fileInfoList;
    }

    private String buildSummary(String mode, List<ImageGenerationGatewayFile> files, boolean usedFallback) {
        String action = "edits".equalsIgnoreCase(mode) ? "图片编辑" : "图片生成";
        StringBuilder names = new StringBuilder();
        for (ImageGenerationGatewayFile file : files) {
            if (file == null || !StringUtils.hasText(file.getFileName())) {
                continue;
            }
            if (names.length() > 0) {
                names.append("、");
            }
            names.append(file.getFileName());
        }
        String fallbackHint = usedFallback ? "；已自动切换兼容接口" : "";
        return action + "完成，共生成 " + files.size() + " 个图片文件：" + names + fallbackHint;
    }

    private String sanitizeOutputName(String rawName) {
        String normalized = StringUtils.hasText(rawName) ? rawName.trim() : "图片生成结果";
        normalized = UNSAFE_FILE_CHARS.matcher(normalized).replaceAll("_").trim();
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
        if (!StringUtils.hasText(normalized)) {
            return "图片生成结果";
        }
        return normalized.length() > 120 ? normalized.substring(0, 120) : normalized;
    }

    private String buildOutputFileName(String baseName, int index, int total, String extension) {
        String stem = baseName;
        int dot = baseName.lastIndexOf('.');
        if (dot > 0) {
            stem = baseName.substring(0, dot);
        }
        String suffix = "." + extension;
        if (total <= 1) {
            return stem + suffix;
        }
        return stem + "_" + (index + 1) + suffix;
    }

    private String extensionFromMime(String mimeType) {
        if (!StringUtils.hasText(mimeType)) {
            return "png";
        }
        String mime = mimeType.toLowerCase(Locale.ROOT);
        if (mime.contains("jpeg") || mime.contains("jpg")) {
            return "jpg";
        }
        if (mime.contains("webp")) {
            return "webp";
        }
        if (mime.contains("gif")) {
            return "gif";
        }
        if (mime.contains("bmp")) {
            return "bmp";
        }
        return "png";
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
