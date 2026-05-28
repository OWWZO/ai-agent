package org.wwz.ai.domain.agent.runtime.tool.common;

import com.alibaba.fastjson.JSON;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpPort;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpRequest;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.dto.CodeInterpreterResponse;
import org.wwz.ai.domain.agent.runtime.dto.File;
import org.wwz.ai.domain.agent.runtime.dto.WebFetchRequest;
import org.wwz.ai.domain.agent.runtime.dto.WebFetchResponse;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单网页抓取工具，负责调用 reactor-tool 的 web_fetch 端点并登记文件产物。
 */
@Slf4j
@Data
public class WebFetchTool implements BaseTool {

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int SUMMARY_MAX_LENGTH = 500;

    private AgentContext agentContext;

    @Override
    public String getName() {
        return "web_fetch";
    }

    @Override
    public String getDescription() {
        String defaultDesc = "这是一个单网页抓取工具，用于读取指定 URL 的正文内容，并把完整正文保存为文件产物。";
        ReactorConfig reactorConfig = requireReactorConfig();
        return StringUtils.isNotBlank(reactorConfig.getWebFetchToolDesc())
                ? reactorConfig.getWebFetchToolDesc()
                : defaultDesc;
    }

    @Override
    public Map<String, Object> toParams() {
        ReactorConfig reactorConfig = requireReactorConfig();
        if (!reactorConfig.getWebFetchToolParams().isEmpty()) {
            return reactorConfig.getWebFetchToolParams();
        }

        Map<String, Object> urlParam = new LinkedHashMap<>();
        urlParam.put("type", "string");
        urlParam.put("description", "需要抓取正文的单个网页 URL，必须以 http:// 或 https:// 开头。");

        Map<String, Object> timeoutParam = new LinkedHashMap<>();
        timeoutParam.put("type", "integer");
        timeoutParam.put("description", "可选下载超时时间，单位秒，默认 30 秒。");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("url", urlParam);
        properties.put("timeout_seconds", timeoutParam);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", Collections.singletonList("url"));
        return parameters;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Object input) {
        try {
            Map<String, Object> params = (Map<String, Object>) input;
            String url = StringUtils.trimToEmpty(valueAsString(params.get("url")));
            if (StringUtils.isBlank(url)) {
                return buildFailurePayload("web_fetch 执行失败：url 不能为空。");
            }

            WebFetchRequest request = WebFetchRequest.builder()
                    .requestId(resolveRequestId())
                    .url(url)
                    .timeoutSeconds(resolveTimeoutSeconds(params))
                    .build();
            ToolArtifactSource artifactSource = agentContext.requireCurrentToolArtifactSource(getName());
            WebFetchResponse response = callWebFetch(request);
            if (response == null) {
                return buildFailurePayload("web_fetch 执行失败：远端服务未返回结果。");
            }
            if (!Integer.valueOf(200).equals(response.getCode())) {
                return buildFailurePayload("web_fetch 执行失败：" + StringUtils.defaultIfBlank(response.getMessage(), "未知错误"));
            }
            if (response.getData() == null) {
                return buildFailurePayload("web_fetch 执行失败：远端服务返回的 data 为空。");
            }

            appendGeneratedArtifacts(response, artifactSource);
            emitFileMessage(response, artifactSource);
            return buildSuccessPayload(response);
        } catch (Exception e) {
            log.error("{} web_fetch execute error, input={}", requestId(), input, e);
            return buildFailurePayload("web_fetch 执行失败：" + StringUtils.defaultIfBlank(e.getMessage(), "未知异常"));
        }
    }

    private WebFetchResponse callWebFetch(WebFetchRequest request) {
        ReactorConfig reactorConfig = requireReactorConfig();
        String responseText;
        try {
            responseText = requireRemoteHttpPort().execute(RemoteHttpRequest.builder()
                    .method("POST")
                    .url(normalizeBaseUrl(reactorConfig.getWebFetchUrl()) + "/v1/tool/web_fetch")
                    .headers(Map.of("Content-Type", "application/json"))
                    .body(JSON.toJSONString(request))
                    .connectTimeoutSeconds(30L)
                    .readTimeoutSeconds((long) request.getTimeoutSeconds())
                    .writeTimeoutSeconds((long) request.getTimeoutSeconds())
                    .callTimeoutSeconds((long) request.getTimeoutSeconds())
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("调用 web_fetch 远端服务失败: " + e.getMessage(), e);
        }
        return JSON.parseObject(responseText, WebFetchResponse.class);
    }

    private void appendGeneratedArtifacts(WebFetchResponse response, ToolArtifactSource artifactSource) {
        for (CodeInterpreterResponse.FileInfo fileInfo : response.safeFileInfo()) {
            if (fileInfo == null) {
                continue;
            }
            File file = File.builder()
                    .fileName(fileInfo.getFileName())
                    .ossUrl(fileInfo.getOssUrl())
                    .domainUrl(fileInfo.getDomainUrl())
                    .fileSize(fileInfo.getFileSize())
                    .description(buildFileDescription(response))
                    .isInternalFile(false)
                    .build();
            agentContext.registerGeneratedArtifact(artifactSource, file);
        }
    }

    private void emitFileMessage(WebFetchResponse response, ToolArtifactSource artifactSource) {
        if (agentContext == null || agentContext.getPrinter() == null || CollectionUtils.isEmpty(response.safeFileInfo())) {
            return;
        }
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("command", "抓取网页正文");
        resultMap.put("fileInfo", new ArrayList<>(response.safeFileInfo()));
        if (artifactSource != null) {
            resultMap.put("toolCallId", artifactSource.getToolCallId());
            resultMap.put("toolName", artifactSource.getToolName());
        }
        String digitalEmployee = agentContext.getToolCollection() == null
                ? null
                : agentContext.getToolCollection().getDigitalEmployee(getName());
        agentContext.getPrinter().send("file", resultMap, digitalEmployee);
    }

    private ToolResultPayload buildSuccessPayload(WebFetchResponse response) {
        WebFetchResponse.DataPayload data = response.getData();
        String title = StringUtils.defaultIfBlank(data.getTitle(), "未命名网页");
        String finalUrl = StringUtils.defaultIfBlank(data.getFinalUrl(), "未知地址");
        String summary = abbreviateContent(data.getContent());
        String observation = "网页抓取完成。标题：" + title
                + "；最终地址：" + finalUrl
                + "；正文摘要：" + summary
                + "；完整内容已保存为文件产物。";
        return ToolResultPayload.builder()
                .toolResult(observation)
                .llmObservation(observation)
                .structuredOutput(null)
                .failed(Boolean.FALSE)
                .build();
    }

    private ToolResultPayload buildFailurePayload(String message) {
        return ToolResultPayload.failure(message, message, null, message);
    }

    private String buildFileDescription(WebFetchResponse response) {
        WebFetchResponse.DataPayload data = response == null ? null : response.getData();
        if (data == null) {
            return "网页抓取正文";
        }
        return StringUtils.defaultIfBlank(data.getTitle(), data.getFinalUrl());
    }

    private Integer resolveTimeoutSeconds(Map<String, Object> params) {
        Integer timeoutSeconds = valueAsInteger(params.get("timeout_seconds"));
        if (timeoutSeconds == null) {
            timeoutSeconds = valueAsInteger(params.get("timeoutSeconds"));
        }
        if (timeoutSeconds == null || timeoutSeconds <= 0) {
            return DEFAULT_TIMEOUT_SECONDS;
        }
        return timeoutSeconds;
    }

    private String resolveRequestId() {
        if (agentContext == null) {
            return "unknown";
        }
        if (StringUtils.isNotBlank(agentContext.getSessionId())) {
            return agentContext.getSessionId();
        }
        return agentContext.getRequestId();
    }

    private String abbreviateContent(String content) {
        String normalized = StringUtils.normalizeSpace(StringUtils.defaultString(content));
        if (normalized.length() <= SUMMARY_MAX_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, SUMMARY_MAX_LENGTH) + "...";
    }

    private String normalizeBaseUrl(String baseUrl) {
        String normalized = StringUtils.trimToEmpty(baseUrl);
        if (StringUtils.isBlank(normalized)) {
            throw new IllegalStateException("web_fetch_url 未配置");
        }
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private String requestId() {
        return agentContext == null ? "unknown" : StringUtils.defaultString(agentContext.getRequestId(), "unknown");
    }

    private String valueAsString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer valueAsInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private ReactorConfig requireReactorConfig() {
        if (agentContext == null || agentContext.getRuntimeDependencies() == null) {
            throw new IllegalStateException("WebFetchTool 缺少 ReactorRuntimeDependencies");
        }
        return agentContext.getRuntimeDependencies().requireReactorConfig();
    }

    private RemoteHttpPort requireRemoteHttpPort() {
        if (agentContext == null || agentContext.getRuntimeDependencies() == null) {
            throw new IllegalStateException("WebFetchTool 缺少 ReactorRuntimeDependencies");
        }
        return agentContext.getRuntimeDependencies().requireRemoteHttpPort();
    }
}
