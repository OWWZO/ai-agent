package org.wwz.ai.domain.agent.runtime.tool.common;

import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.dto.File;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.util.StringUtil;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.ImageGenerationExecuteCommand;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.ImageGenerationExecutionResult;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.WorkspaceImageFile;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ImageGenerationToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolFileRef;
import org.wwz.ai.domain.agent.reactor.service.imagegeneration.IImageGenerationExecutionKernel;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 图片生成工具，负责把文生图 / 图生图请求转发到 reactor-tool。
 */
@Slf4j
@Data
public class ImageGenerationTool implements BaseTool {

    private static final String MODE_IMAGES = "images";
    private static final String MODE_EDITS = "edits";
    private static final int DEFAULT_TIMEOUT_SECONDS = 900;

    private AgentContext agentContext;

    @Override
    public String getName() {
        return "image_generation_tool";
    }

    @Override
    public String getDescription() {
        String defaultDesc = "这是一个图片生成工具，支持文生图和图生图。用户要求基于当前轮上传图片修改、换风格、扩图时应优先调用它；未显式传 fileNames 时可自动复用当前轮图片。";
        ReactorConfig reactorConfig = requireReactorConfig();
        return StringUtils.isNotBlank(reactorConfig.getImageGenerationToolDesc())
                ? reactorConfig.getImageGenerationToolDesc()
                : defaultDesc;
    }

    @Override
    public Map<String, Object> toParams() {
        ReactorConfig reactorConfig = requireReactorConfig();
        if (!reactorConfig.getImageGenerationToolParams().isEmpty()) {
            return reactorConfig.getImageGenerationToolParams();
        }

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("prompt", buildStringParam("图片生成或图片编辑指令，需要写清楚画面主体、风格、构图、质感以及修改要求。"));
        properties.put("mode", buildEnumParam("生成模式，images 表示文生图，edits 表示图生图。用户明确要求忽略上传图片时传 images。", Arrays.asList(MODE_IMAGES, MODE_EDITS)));
        properties.put("fileNames", buildStringArrayParam("图生图时要使用的参考图片文件名列表，文件必须来自当前会话可用图片；未显式传入时可自动复用当前轮上传图片。"));
        properties.put("maskFileNames", buildStringArrayParam("可选遮罩图文件名列表，与 fileNames 按顺序对应，建议使用已标红或涂抹编辑区域的图片。"));
        properties.put("fileName", buildStringParam("输出图片文件名称，可不带后缀；未传时默认使用“图片生成结果”。"));
        properties.put("fileDescription", buildStringParam("输出图片文件描述，用一句中文概括图片内容或用途。"));
        properties.put("size", buildStringParam("输出尺寸，例如 1024x1024、1536x1024。"));
        properties.put("n", buildIntegerParam("期望生成的图片数量，默认 1。"));
        properties.put("model", buildStringParam("可选的图片模型名称，例如 gpt-image-2。"));

        parameters.put("properties", properties);
        parameters.put("required", Collections.singletonList("prompt"));
        return parameters;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Object input) {
        try {
            Map<String, Object> params = (Map<String, Object>) input;
            String prompt = StringUtils.trimToEmpty(valueAsString(params.get("prompt")));
            if (StringUtils.isBlank(prompt)) {
                return buildFailurePayload("image_generation_tool 执行失败：prompt 不能为空。");
            }

            String mode = StringUtils.trimToEmpty(valueAsString(params.get("mode")));
            List<String> fileNames = toStringList(params.get("fileNames"));
            // 仅在未显式要求文生图时，才兜底复用当前轮图片，避免误伤明确的 images 模式。
            if (fileNames.isEmpty() && shouldReuseContextImages(mode)) {
                fileNames = collectContextImageFileNames();
            }
            List<String> maskFileNames = toStringList(params.get("maskFileNames"));
            ToolArtifactSource artifactSource = agentContext.requireCurrentToolArtifactSource(getName());
            ImageGenerationExecutionResult result = requireKernel().execute(ImageGenerationExecuteCommand.builder()
                    // 图片产物目录按 session 归档，和其他工具保持一致，便于会话内统一查看文件。
                    .requestId(agentContext.getSessionId())
                    .prompt(prompt)
                    .mode(StringUtils.isBlank(mode) ? null : mode)
                    .fileNames(fileNames)
                    .maskFileNames(maskFileNames)
                    .fileName(resolveOutputFileName(params.get("fileName")))
                    .fileDescription(resolveOutputDescription(params.get("fileDescription"), prompt))
                    .model(StringUtils.trimToNull(valueAsString(params.get("model"))))
                    .size(StringUtils.trimToNull(valueAsString(params.get("size"))))
                    .n(resolveInteger(params.get("n"), 1))
                    .timeoutSeconds(DEFAULT_TIMEOUT_SECONDS)
                    .build());
            appendGeneratedArtifacts(result, artifactSource);
            emitFileMessage(result, artifactSource);
            return buildSuccessPayload(result);
        } catch (Exception e) {
            log.error("{} image_generation_tool error, input={}", agentContext.getRequestId(), input, e);
            return buildFailurePayload("image_generation_tool 执行失败：" + e.getMessage());
        }
    }

    private IImageGenerationExecutionKernel requireKernel() {
        if (agentContext == null || agentContext.getRuntimeDependencies() == null) {
            throw new IllegalStateException("ImageGenerationTool 缺少 ReactorRuntimeDependencies");
        }
        return agentContext.getRuntimeDependencies().requireImageGenerationExecutionKernel();
    }

    private void appendGeneratedArtifacts(ImageGenerationExecutionResult result, ToolArtifactSource artifactSource) {
        if (result == null || CollectionUtils.isEmpty(result.getFiles())) {
            return;
        }
        for (WorkspaceImageFile fileInfo : result.getFiles()) {
            File file = File.builder()
                    .fileName(fileInfo.getFileName())
                    .fileSize(fileInfo.getFileSize() == null ? null : Math.toIntExact(fileInfo.getFileSize()))
                    .ossUrl(fileInfo.getOssUrl())
                    .domainUrl(fileInfo.getDomainUrl())
                    .description(result.getSummary())
                    .isInternalFile(false)
                    .build();
            agentContext.registerGeneratedArtifact(artifactSource, file);
        }
    }

    private void emitFileMessage(ImageGenerationExecutionResult result, ToolArtifactSource artifactSource) {
        if (result == null || CollectionUtils.isEmpty(result.getFiles())) {
            return;
        }
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("command", "生成图片");
        resultMap.put("fileInfo", result.getFiles());
        if (artifactSource != null) {
            resultMap.put("toolCallId", artifactSource.getToolCallId());
            resultMap.put("toolName", artifactSource.getToolName());
        }
        String messageId = StringUtil.getUUID();
        String digitalEmployee = agentContext.getToolCollection().getDigitalEmployee(getName());
        agentContext.getPrinter().send(messageId, "file", resultMap, digitalEmployee, true);
    }

    private List<String> collectContextImageFileNames() {
        if (agentContext.getProductFiles() == null) {
            return Collections.emptyList();
        }
        return agentContext.getProductFiles().stream()
                .filter(Objects::nonNull)
                .filter(this::isImageFile)
                .map(this::resolveImageReference)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());
    }

    private boolean shouldReuseContextImages(String mode) {
        return StringUtils.isBlank(mode) || MODE_EDITS.equals(mode);
    }

    private boolean isImageFile(File file) {
        return file != null && isImageFileName(file.getFileName());
    }

    private boolean isImageFileName(String fileName) {
        if (StringUtils.isBlank(fileName) || !fileName.contains(".")) {
            return false;
        }
        String extension = StringUtils.substringAfterLast(fileName, ".").toLowerCase(Locale.ROOT);
        return Arrays.asList("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg").contains(extension);
    }

    /**
     * 复用会话图片时优先使用可直接访问的 URL，避免下游按当前 requestId 重拼预览地址导致 404。
     */
    private String resolveImageReference(File file) {
        if (StringUtils.isNotBlank(file.getDomainUrl())) {
            return file.getDomainUrl();
        }
        if (StringUtils.isNotBlank(file.getOssUrl())) {
            return file.getOssUrl();
        }
        return file.getFileName();
    }

    private String resolveOutputFileName(Object rawValue) {
        String fileName = StringUtil.removeSpecialChars(StringUtils.trimToEmpty(valueAsString(rawValue)));
        if (StringUtils.isBlank(fileName)) {
            return "图片生成结果";
        }
        return fileName;
    }

    private String resolveOutputDescription(Object rawValue, String prompt) {
        String description = StringUtils.trimToEmpty(valueAsString(rawValue));
        if (StringUtils.isNotBlank(description)) {
            return description;
        }
        return StringUtils.abbreviate(prompt, 80);
    }

    private List<String> toStringList(Object rawValue) {
        if (rawValue == null) {
            return new ArrayList<>();
        }
        if (rawValue instanceof List<?> rawList) {
            return rawList.stream()
                    .map(this::valueAsString)
                    .filter(StringUtils::isNotBlank)
                    .map(StringUtils::trim)
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        String text = valueAsString(rawValue);
        if (StringUtils.isBlank(text)) {
            return new ArrayList<>();
        }
        return Arrays.stream(text.split("[,，]"))
                .map(StringUtils::trim)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private Integer resolveInteger(Object rawValue, int defaultValue) {
        if (rawValue instanceof Number number) {
            return number.intValue();
        }
        String text = valueAsString(rawValue);
        if (StringUtils.isBlank(text)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private String valueAsString(Object rawValue) {
        return rawValue == null ? null : String.valueOf(rawValue);
    }

    /**
     * 生图结果需要同时保留 prompt、摘要和图片文件引用，便于后续 replay 还原。
     */
    private ToolResultPayload buildSuccessPayload(ImageGenerationExecutionResult result) {
        String summary = normalizeSummary(result);
        ImageGenerationToolOutput structuredOutput = ImageGenerationToolOutput.builder()
                .prompt(result.getPrompt())
                .mode(result.getMode())
                .summary(summary)
                .size(result.getSize())
                .batchCount(result.getBatchCount())
                .sourceImageCount(result.getSourceImageCount())
                .maskImageCount(result.getMaskImageCount())
                .usedFallback(result.getUsedFallback())
                .fileRefs(toToolFileRefs(result.getFiles()))
                .build();
        return ToolResultPayload.structured(summary, summary, structuredOutput);
    }

    /**
     * 图片生成失败时返回最小 typed output，避免 rich tool 退化成纯字符串。
     */
    private ToolResultPayload buildFailurePayload(String message) {
        return ToolResultPayload.failure(
                message,
                message,
                ImageGenerationToolOutput.builder()
                        .summary(message)
                        .build(),
                message
        );
    }

    private String normalizeSummary(ImageGenerationExecutionResult result) {
        String summary = result == null ? null : StringUtils.trimToNull(result.getSummary());
        if (result != null && !CollectionUtils.isEmpty(result.getFiles())) {
            String fileNames = result.getFiles().stream()
                    .map(WorkspaceImageFile::getFileName)
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.joining("、"));
            if (StringUtils.isBlank(summary)) {
                summary = "已生成图片文件：" + fileNames;
            }
        }
        return StringUtils.defaultIfBlank(summary, "image_generation_tool 执行完成");
    }

    private List<ToolFileRef> toToolFileRefs(List<WorkspaceImageFile> files) {
        if (CollectionUtils.isEmpty(files)) {
            return List.of();
        }
        return files.stream()
                .filter(Objects::nonNull)
                .map(file -> ToolFileRef.builder()
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

    private Map<String, Object> buildStringParam(String description) {
        Map<String, Object> param = new HashMap<>();
        param.put("type", "string");
        param.put("description", description);
        return param;
    }

    private Map<String, Object> buildIntegerParam(String description) {
        Map<String, Object> param = new HashMap<>();
        param.put("type", "integer");
        param.put("description", description);
        return param;
    }

    private Map<String, Object> buildEnumParam(String description, List<String> enumValues) {
        Map<String, Object> param = buildStringParam(description);
        param.put("enum", enumValues);
        return param;
    }

    private Map<String, Object> buildStringArrayParam(String description) {
        Map<String, Object> param = new HashMap<>();
        param.put("type", "array");
        param.put("description", description);

        Map<String, Object> items = new HashMap<>();
        items.put("type", "string");
        param.put("items", items);
        return param;
    }

    private ReactorConfig requireReactorConfig() {
        if (agentContext == null || agentContext.getRuntimeDependencies() == null) {
            throw new IllegalStateException("ImageGenerationTool 缺少 ReactorRuntimeDependencies");
        }
        return agentContext.getRuntimeDependencies().requireReactorConfig();
    }
}
