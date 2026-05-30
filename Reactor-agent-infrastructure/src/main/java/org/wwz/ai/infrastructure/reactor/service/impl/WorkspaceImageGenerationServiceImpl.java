package org.wwz.ai.infrastructure.reactor.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.wwz.ai.domain.agent.runtime.util.StringUtil;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.ImageGenerationExecuteCommand;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.ImageGenerationExecutionResult;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.WorkspaceImageFile;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.WorkspaceImageGenerationCommand;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.WorkspaceImageGenerationHistoryBatch;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.WorkspaceImageGenerationHistoryPage;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.WorkspaceImageGenerationResult;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ImageGenerationToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolOutputNames;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolOutputView;
import org.wwz.ai.domain.agent.reactor.service.IWorkspaceImageGenerationService;
import org.wwz.ai.domain.agent.reactor.service.imagegeneration.IImageGenerationBatchPersistenceService;
import org.wwz.ai.domain.agent.reactor.service.imagegeneration.IImageGenerationExecutionKernel;
import org.wwz.ai.domain.agent.ledger.tooloutput.ToolOutputReader;
import org.wwz.ai.infrastructure.dao.reactor.IToolOutputImageGenerationDao;

import javax.annotation.Resource;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 生图工作台服务实现。
 * Phase 2A 先由 infrastructure 承接 DAO 读取链路，后续再继续抽离更细的 repository seam。
 */
@Slf4j
@Service
public class WorkspaceImageGenerationServiceImpl implements IWorkspaceImageGenerationService {

    private static final String MODE_IMAGES = "images";
    private static final String MODE_EDITS = "edits";
    private static final String DEFAULT_OUTPUT_NAME = "图片生成结果";
    private static final String DEFAULT_IMAGE_SIZE = "1024x1024";
    private static final int DEFAULT_TIMEOUT_SECONDS = 900;
    private static final int DEFAULT_BATCH_SIZE = 10;
    private static final int MAX_BATCH_SIZE = 50;

    @Resource
    private IImageGenerationExecutionKernel imageGenerationExecutionKernel;
    @Resource
    private IImageGenerationBatchPersistenceService imageGenerationBatchPersistenceService;
    @Resource
    private IToolOutputImageGenerationDao toolOutputImageGenerationDao;
    @Resource
    private ToolOutputReader toolOutputReader;

    @Override
    public WorkspaceImageGenerationResult generate(WorkspaceImageGenerationCommand command) {
        validateGenerateRequest(command);

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
        ImageGenerationExecutionResult result = imageGenerationExecutionKernel.execute(ImageGenerationExecuteCommand.builder()
                .requestId(requestId)
                .prompt(command.getPrompt().trim())
                .mode(mode)
                .fileNames(sourceImages)
                .maskFileNames(maskImages)
                .fileName(resolveOutputFileName(command.getFileName()))
                .fileDescription(resolveFileDescription(command.getFileDescription(), command.getPrompt()))
                .model(StringUtils.trimToNull(command.getModel()))
                .size(resolveSize(command.getSize()))
                .n(normalizeBatchSize(command.getN()))
                .timeoutSeconds(DEFAULT_TIMEOUT_SECONDS)
                .build());
        if (CollectionUtils.isEmpty(result.getFiles())) {
            throw new IllegalStateException("上游未返回可识别的图片结果");
        }

        imageGenerationBatchPersistenceService.persistWorkspaceBatch(requestId, result);
        log.info("生图工作台生成成功 requestId={}, fileCount={}", requestId, result.getFiles().size());

        return WorkspaceImageGenerationResult.builder()
                .data(StringUtils.defaultIfBlank(result.getSummary(), "生成完成"))
                .fileInfo(result.getFiles())
                .requestId(requestId)
                .mode(result.getMode())
                .usedFallback(result.getUsedFallback())
                .rawResponse(result.getRawResponse())
                .build();
    }

    @Override
    public WorkspaceImageGenerationHistoryPage queryHistory(int pageNo, int pageSize) {
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.min(pageSize > 0 ? pageSize : DEFAULT_BATCH_SIZE, MAX_BATCH_SIZE);
        int total = toolOutputImageGenerationDao.countByRequestSource(ExecutionLedgerConstants.REQUEST_SOURCE_WORKSPACE);
        if (total <= 0) {
            return WorkspaceImageGenerationHistoryPage.builder()
                    .total(0)
                    .list(Collections.emptyList())
                    .build();
        }
        int offset = (safePageNo - 1) * safePageSize;
        List<Map<String, Object>> rows = toolOutputImageGenerationDao.queryPageByRequestSource(
                ExecutionLedgerConstants.REQUEST_SOURCE_WORKSPACE, offset, safePageSize);
        if (CollectionUtils.isEmpty(rows)) {
            return WorkspaceImageGenerationHistoryPage.builder()
                    .total(total)
                    .list(Collections.emptyList())
                    .build();
        }
        List<WorkspaceImageGenerationHistoryBatch> list = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            ToolOutputView outputView = toolOutputReader.readDirect(stringValue(row, "request_id"), stringValue(row, "tool_call_id"))
                    .orElse(buildFallbackView(row));
            list.add(toHistoryBatch(outputView));
        }
        return WorkspaceImageGenerationHistoryPage.builder()
                .total(total)
                .list(list)
                .build();
    }

    private void validateGenerateRequest(WorkspaceImageGenerationCommand command) {
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
        List<String> normalized = new ArrayList<>(fileNames.size());
        for (String fileName : fileNames) {
            if (org.springframework.util.StringUtils.hasText(fileName)) {
                normalized.add(fileName.trim());
            }
        }
        return normalized;
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

    private ToolOutputView buildFallbackView(Map<String, Object> row) {
        ImageGenerationToolOutput output = ImageGenerationToolOutput.builder()
                .prompt(stringValue(row, "prompt"))
                .mode(stringValue(row, "mode"))
                .summary(stringValue(row, "summary"))
                .size(stringValue(row, "size"))
                .batchCount(integerValue(row, "batch_count"))
                .sourceImageCount(integerValue(row, "source_image_count"))
                .maskImageCount(integerValue(row, "mask_image_count"))
                .usedFallback(booleanValue(row, "used_fallback"))
                .fileRefs(List.of())
                .build();
        return ToolOutputView.builder()
                .toolName(ToolOutputNames.IMAGE_GENERATION)
                .requestId(stringValue(row, "request_id"))
                .requestSource(stringValue(row, "request_source"))
                .toolCallId(stringValue(row, "tool_call_id"))
                .status(integerValue(row, "status"))
                .errorMsg(stringValue(row, "error_msg"))
                .createdAt(localDateTimeValue(row, "created_at"))
                .structuredOutput(output)
                .build();
    }

    private WorkspaceImageGenerationHistoryBatch toHistoryBatch(ToolOutputView outputView) {
        ImageGenerationToolOutput output = outputView.getStructuredOutput() instanceof ImageGenerationToolOutput imageOutput
                ? imageOutput
                : ImageGenerationToolOutput.builder().build();
        return WorkspaceImageGenerationHistoryBatch.builder()
                .requestId(outputView.getRequestId())
                .prompt(output.getPrompt())
                .mode(output.getMode())
                .size(output.getSize())
                .batchCount(output.getBatchCount())
                .sourceImageCount(output.getSourceImageCount())
                .maskImageCount(output.getMaskImageCount())
                .usedFallback(output.getUsedFallback())
                .createdAt(outputView.getCreatedAt())
                .images(toWorkspaceFiles(output))
                .build();
    }

    private List<WorkspaceImageFile> toWorkspaceFiles(ImageGenerationToolOutput output) {
        if (output == null || CollectionUtils.isEmpty(output.getFileRefs())) {
            return List.of();
        }
        List<WorkspaceImageFile> files = new ArrayList<>(output.getFileRefs().size());
        for (var fileRef : output.getFileRefs()) {
            if (fileRef == null) {
                continue;
            }
            files.add(WorkspaceImageFile.builder()
                    .fileName(fileRef.getFileName())
                    .ossUrl(fileRef.getOssUrl())
                    .domainUrl(fileRef.getDomainUrl())
                    .downloadUrl(StringUtils.firstNonBlank(fileRef.getDownloadUrl(), fileRef.getOssUrl(), fileRef.getDomainUrl()))
                    .previewUrl(StringUtils.firstNonBlank(fileRef.getPreviewUrl(), fileRef.getDomainUrl(), fileRef.getDownloadUrl(), fileRef.getOssUrl()))
                    .fileSize(fileRef.getFileSize())
                    .mimeType(fileRef.getMimeType())
                    .build());
        }
        return files;
    }

    private String stringValue(Map<String, Object> row, String key) {
        Object value = row == null ? null : row.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Integer integerValue(Map<String, Object> row, String key) {
        Object value = row == null ? null : row.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Boolean booleanValue(Map<String, Object> row, String key) {
        Object value = row == null ? null : row.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number number) {
            return number.intValue() == 1;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private java.time.LocalDateTime localDateTimeValue(Map<String, Object> row, String key) {
        Object value = row == null ? null : row.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof java.time.LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return null;
    }
}
