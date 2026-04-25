package org.wwz.ai.domain.agent.reactor.agent.tool.common;

import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationContext;
import org.wwz.ai.domain.agent.reactor.agent.agent.AgentContext;
import org.wwz.ai.domain.agent.reactor.agent.dto.CodeInterpreterResponse;
import org.wwz.ai.domain.agent.reactor.agent.dto.File;
import org.wwz.ai.domain.agent.reactor.agent.dto.ImageGenerationRequest;
import org.wwz.ai.domain.agent.reactor.agent.tool.BaseTool;
import org.wwz.ai.domain.agent.reactor.agent.util.SpringContextHolder;
import org.wwz.ai.domain.agent.reactor.agent.util.StringUtil;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 图片生成工具，负责把文生图 / 图生图请求转发到 reactor-tool。
 */
@Slf4j
@Data
public class ImageGenerationTool implements BaseTool {

    private AgentContext agentContext;

    @Override
    public String getName() {
        return "image_generation_tool";
    }

    @Override
    public String getDescription() {
        String defaultDesc = "这是一个图片生成工具，支持文生图和基于已有图片的图生图，生成后的图片会作为会话产物保存。";
        ReactorConfig reactorConfig = SpringContextHolder.getApplicationContext().getBean(ReactorConfig.class);
        return StringUtils.isNotBlank(reactorConfig.getImageGenerationToolDesc())
                ? reactorConfig.getImageGenerationToolDesc()
                : defaultDesc;
    }

    @Override
    public Map<String, Object> toParams() {
        ReactorConfig reactorConfig = SpringContextHolder.getApplicationContext().getBean(ReactorConfig.class);
        if (!reactorConfig.getImageGenerationToolParams().isEmpty()) {
            return reactorConfig.getImageGenerationToolParams();
        }

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("prompt", buildStringParam("图片生成或图片编辑指令，需要写清楚画面主体、风格、构图、质感以及修改要求。"));
        properties.put("mode", buildEnumParam("生成模式，images 表示文生图，edits 表示图生图。", Arrays.asList("images", "edits")));
        properties.put("fileNames", buildStringArrayParam("图生图时要使用的参考图片文件名列表，文件必须来自当前会话可用图片。"));
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
                return "image_generation_tool 执行失败：prompt 不能为空。";
            }

            String mode = StringUtils.trimToEmpty(valueAsString(params.get("mode")));
            List<String> fileNames = toStringList(params.get("fileNames"));
            if ("edits".equals(mode) && fileNames.isEmpty()) {
                fileNames = collectContextImageFileNames();
            }
            List<String> maskFileNames = toStringList(params.get("maskFileNames"));

            ImageGenerationRequest request = ImageGenerationRequest.builder()
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
                    .timeoutSeconds(300)
                    .stream(true)
                    .build();

            Future<String> future = callImageGenerationStream(request);
            return future.get();
        } catch (Exception e) {
            log.error("{} image_generation_tool error, input={}", agentContext.getRequestId(), input, e);
            return "image_generation_tool 执行失败：" + e.getMessage();
        }
    }

    /**
     * 调用 reactor-tool 图片生成端点，并把最终产物同步回会话上下文。
     */
    public CompletableFuture<String> callImageGenerationStream(ImageGenerationRequest requestPayload) {
        CompletableFuture<String> future = new CompletableFuture<>();
        try {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(5, TimeUnit.MINUTES)
                    .readTimeout(10, TimeUnit.MINUTES)
                    .writeTimeout(5, TimeUnit.MINUTES)
                    .callTimeout(10, TimeUnit.MINUTES)
                    .build();

            ApplicationContext applicationContext = SpringContextHolder.getApplicationContext();
            ReactorConfig reactorConfig = applicationContext.getBean(ReactorConfig.class);
            String url = reactorConfig.getImageGenerationUrl() + "/v1/tool/image_generation";
            RequestBody requestBody = RequestBody.create(
                    MediaType.parse("application/json"),
                    JSONObject.toJSONString(requestPayload)
            );

            log.info("{} image_generation_tool request {}", agentContext.getRequestId(), JSONObject.toJSONString(requestPayload));
            Request request = new Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    log.error("{} image_generation_tool on failure", agentContext.getRequestId(), e);
                    future.completeExceptionally(e);
                }

                @Override
                public void onResponse(Call call, Response response) {
                    CodeInterpreterResponse finalResponse = CodeInterpreterResponse.builder()
                            .data("image_generation_tool 执行失败")
                            .build();
                    try (ResponseBody responseBody = response.body()) {
                        if (!response.isSuccessful() || responseBody == null) {
                            future.completeExceptionally(new IOException("Unexpected response code: " + response));
                            return;
                        }

                        String line;
                        String messageId = StringUtil.getUUID();
                        String digitalEmployee = agentContext.getToolCollection().getDigitalEmployee(getName());
                        BufferedReader reader = new BufferedReader(new InputStreamReader(responseBody.byteStream(), StandardCharsets.UTF_8));
                        while ((line = reader.readLine()) != null) {
                            if (!line.startsWith("data: ")) {
                                continue;
                            }

                            String data = line.substring(6);
                            if ("[DONE]".equals(data) || data.startsWith("heartbeat")) {
                                continue;
                            }

                            CodeInterpreterResponse chunkResponse = JSONObject.parseObject(data, CodeInterpreterResponse.class);
                            if (chunkResponse == null) {
                                continue;
                            }
                            finalResponse = chunkResponse;

                            if (Boolean.TRUE.equals(chunkResponse.getIsFinal())) {
                                handleFinalFiles(chunkResponse, requestPayload, messageId, digitalEmployee);
                            }
                        }
                    } catch (Exception e) {
                        log.error("{} image_generation_tool request error", agentContext.getRequestId(), e);
                        future.completeExceptionally(e);
                        return;
                    }

                    String summary = StringUtils.trimToNull(finalResponse.getData());
                    if (finalResponse.getFileInfo() != null && !finalResponse.getFileInfo().isEmpty()) {
                        String fileNames = finalResponse.getFileInfo().stream()
                                .map(CodeInterpreterResponse.FileInfo::getFileName)
                                .filter(StringUtils::isNotBlank)
                                .collect(Collectors.joining("、"));
                        if (StringUtils.isBlank(summary)) {
                            summary = "已生成图片文件：" + fileNames;
                        }
                    }
                    future.complete(StringUtils.defaultIfBlank(summary, "image_generation_tool 执行完成"));
                }
            });
        } catch (Exception e) {
            log.error("{} image_generation_tool request error", agentContext.getRequestId(), e);
            future.completeExceptionally(e);
        }
        return future;
    }

    private void handleFinalFiles(CodeInterpreterResponse response,
                                  ImageGenerationRequest requestPayload,
                                  String messageId,
                                  String digitalEmployee) {
        if (response.getFileInfo() == null || response.getFileInfo().isEmpty()) {
            return;
        }

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("command", "生成图片");
        resultMap.put("fileInfo", response.getFileInfo());

        for (CodeInterpreterResponse.FileInfo fileInfo : response.getFileInfo()) {
            File file = File.builder()
                    .fileName(fileInfo.getFileName())
                    .fileSize(fileInfo.getFileSize())
                    .ossUrl(fileInfo.getOssUrl())
                    .domainUrl(fileInfo.getDomainUrl())
                    .description(requestPayload.getFileDescription())
                    .isInternalFile(false)
                    .build();
            agentContext.getProductFiles().add(file);
            agentContext.getTaskProductFiles().add(file);
        }

        agentContext.getPrinter().send(messageId, "file", resultMap, digitalEmployee, true);
    }

    private List<String> collectContextImageFileNames() {
        if (agentContext.getProductFiles() == null) {
            return Collections.emptyList();
        }
        return agentContext.getProductFiles().stream()
                .map(File::getFileName)
                .filter(this::isImageFileName)
                .collect(Collectors.toList());
    }

    private boolean isImageFileName(String fileName) {
        if (StringUtils.isBlank(fileName) || !fileName.contains(".")) {
            return false;
        }
        String extension = StringUtils.substringAfterLast(fileName, ".").toLowerCase(Locale.ROOT);
        return Arrays.asList("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg").contains(extension);
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
