package org.wwz.ai.domain.agent.runtime.tool.common;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.dto.File;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.util.StringUtil;
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

    private static final String DESCRIPTION = "本工具用于文生图和图生图。用户要求基于当前轮上传图片修改、换风格、扩图时应优先调用它；未显式传 fileNames 时系统可自动复用当前轮上传图片，输出图片会自动保存为当前会话产物。";
    private static final String PARAMS = """
            {"type":"object","properties":{"prompt":{"type":"string","description":"图片生成或编辑指令，需要明确主体、风格、构图、质感及修改要求。"},"mode":{"type":"string","enum":["images","edits"],"description":"生成模式，images 表示文生图，edits 表示图生图。用户明确要求忽略上传图片时传 images。"},"fileNames":{"type":"array","items":{"type":"string"},"description":"图生图参考图片列表，可来自当前会话已有图片；未显式传入时系统可自动复用当前轮上传图片。"},"maskFileNames":{"type":"array","items":{"type":"string"},"description":"可选的涂抹参考图列表，与 fileNames 一一对应。"},"size":{"type":"string","description":"输出尺寸，例如 1024x1024、1536x1024。"},"n":{"type":"integer","description":"期望生成的图片数量。"},"model":{"type":"string","description":"可选的图片模型名称，例如 gpt-image-2。"}},"required":["prompt"]}
            """;

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
        return DESCRIPTION;
    }

    @Override
    public Map<String, Object> toParams() {
        return JSON.parseObject(PARAMS);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Object input) {
        try {
            // 工具调用分为四个阶段：校验 prompt、解析当前会话中的输入图片引用、
            // 委托执行内核生成产物、再把同一批文件分别登记到 artifact 和 SSE 文件事件。
            // typed output 最后返回给模型/回放链路，三种输出必须使用同一执行结果。
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
            } else {
                // Agent 常传裸文件名；先映射到 productFiles 里的可访问 URL，避免按 sessionId 重拼 preview 导致 404。
                fileNames = resolveReferenceList(fileNames);
            }
            List<String> maskFileNames = resolveReferenceList(toStringList(params.get("maskFileNames")));
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
        // artifact registry 是 ledger 可追踪文件引用的入口；这里登记的是生成结果，
        // 不是模型原始 URL，后续历史回放和前端预览都依赖这一稳定引用。
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
        // SSE 文件事件服务于当前轮即时展示，不能替代 artifact 持久化；因此即使
        // 前端事件发送失败，typed tool result 仍由 execute 的返回值负责闭环。
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

    /**
     * 把 agent 传入的 fileNames/maskFileNames 解析为可下载引用。
     * 优先保留 http(s)/data URL；裸文件名则匹配会话 productFiles 的 domainUrl/ossUrl。
     */
    private List<String> resolveReferenceList(List<String> refs) {
        if (refs == null || refs.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> resolved = new ArrayList<>(refs.size());
        for (String ref : refs) {
            String value = StringUtils.trimToEmpty(ref);
            if (StringUtils.isBlank(value)) {
                continue;
            }
            if (looksLikeDirectUrl(value)) {
                resolved.add(value);
                continue;
            }
            String mapped = findProductFileUrl(value);
            if (StringUtils.isNotBlank(mapped)) {
                resolved.add(mapped);
            } else {
                // 保留原值，交给下游 previewBaseUrl + requestId 兜底拼接
                resolved.add(value);
            }
        }
        return resolved;
    }

    private boolean looksLikeDirectUrl(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://")
                || lower.startsWith("https://")
                || lower.startsWith("data:")
                || lower.contains("/preview/")
                || lower.contains("/download/");
    }

    private String findProductFileUrl(String fileNameOrPath) {
        if (agentContext == null || agentContext.getProductFiles() == null) {
            return null;
        }
        String wanted = basename(fileNameOrPath);
        if (StringUtils.isBlank(wanted)) {
            return null;
        }
        for (File file : agentContext.getProductFiles()) {
            if (file == null || StringUtils.isBlank(file.getFileName())) {
                continue;
            }
            if (!wanted.equalsIgnoreCase(basename(file.getFileName()))) {
                continue;
            }
            String url = resolveImageReference(file);
            if (StringUtils.isNotBlank(url) && !wanted.equals(url)) {
                return url;
            }
        }
        for (File file : agentContext.getProductFiles()) {
            if (file == null) {
                continue;
            }
            for (String candidate : Arrays.asList(file.getDomainUrl(), file.getOssUrl())) {
                if (StringUtils.isBlank(candidate)) {
                    continue;
                }
                if (wanted.equalsIgnoreCase(basename(candidate))) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static String basename(String path) {
        if (path == null) {
            return null;
        }
        String value = path.trim();
        int q = value.indexOf('?');
        if (q >= 0) {
            value = value.substring(0, q);
        }
        int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        return slash >= 0 ? value.substring(slash + 1) : value;
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
        // 返回结构同时保留摘要、生成参数和稳定文件引用：摘要给模型继续推理，
        // fileRefs/produced_files 给 ledger、replay 及 document_generate 等下游复用。
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
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tool", "image_generation");
        data.put("ok", Boolean.TRUE);
        data.put("summary", summary);
        data.put("prompt", result.getPrompt());
        data.put("mode", result.getMode());
        data.put("size", result.getSize());
        if (result.getBatchCount() != null) {
            data.put("batchCount", result.getBatchCount());
        }
        if (!CollectionUtils.isEmpty(result.getFiles())) {
            List<Map<String, Object>> produced = new ArrayList<>();
            for (WorkspaceImageFile file : result.getFiles()) {
                if (file == null) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("file_name", file.getFileName());
                row.put("mimeType", file.getMimeType());
                String url = StringUtil.firstNonBlank(
                        file.getPreviewUrl(), file.getDomainUrl(), file.getDownloadUrl(), file.getOssUrl());
                if (StringUtils.isNotBlank(url)) {
                    row.put("url", url);
                }
                produced.add(row);
            }
            data.put("produced_files", produced);
            data.put("hint", "Use produced_files.url as image.url for document_generate when needed.");
        }
        return ToolResultPayload.fromData(data, structuredOutput);
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

}
