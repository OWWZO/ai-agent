package org.wwz.ai.domain.agent.runtime.tool.browser;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.adapter.port.BrowserRelayPort;
import org.wwz.ai.domain.agent.adapter.port.BrowserRpcResult;
import org.wwz.ai.domain.agent.adapter.port.FileArtifactPort;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.dto.File;
import org.wwz.ai.domain.agent.runtime.dto.FileRequest;
import org.wwz.ai.domain.agent.runtime.dto.FileResponse;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;

import java.time.Duration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
public class BrowserScreenshotTool extends AbstractBrowserRelayTool {

    public static final String TOOL_NAME = "browser_screenshot";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "截取当前 Agent Tab。annotate=true 时像 OpenCLI 一样在截图上叠加 ref 编号。observation 只返回 artifact 引用，不包含 base64。右侧工作区显示视口。";
    }

    @Override
    public Map<String, Object> toParams() {
        return Map.of(
                "type", "object",
                "properties", Map.of("fullPage", Map.of("type", "boolean"), "annotate", Map.of("type", "boolean", "description", "在截图上叠加 OpenCLI ref 编号"))
        );
    }

    @Override
    protected Duration rpcTimeout() {
        return Duration.ofSeconds(60);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Object input) {
        Map<String, Object> params = input instanceof Map<?, ?> map ? new LinkedHashMap<>((Map<String, Object>) map) : new LinkedHashMap<>();
        String visitorId = agentContext == null ? null : StringUtils.trimToNull(agentContext.getVisitorId());
        if (visitorId == null) {
            return ToolResultPayload.failure("缺少访客身份", "缺少访客身份", null, "missing visitorId");
        }
        BrowserRelayPort port = agentContext.getRuntimeDependencies() == null
                ? null
                : agentContext.getRuntimeDependencies().getOptionalBrowserRelayPort();
        if (port == null || !port.isOnline(visitorId)) {
            return ToolResultPayload.failure("浏览器未连接", "浏览器未连接", null, "browser_offline");
        }
        Duration timeout = rpcTimeout();
        params.put("deadlineAt", System.currentTimeMillis() + timeout.toMillis());
        BrowserRpcResult result = port.call(visitorId, "screenshot", params, timeout);
        if (result == null || !result.isOk() || result.getData() == null) {
            String error = result == null ? "截图失败" : StringUtils.defaultIfBlank(result.getError(), "截图失败");
            return ToolResultPayload.failure(error, error, null, result == null ? "rpc_failed" : result.getErrorCode());
        }
        Map<String, Object> fields = new LinkedHashMap<>(result.getData());
        Object raw = fields.remove("imageBase64");
        String imageBase64 = raw == null ? null : String.valueOf(raw).replaceAll("\\s", "");
        String artifactRef = uploadPng(imageBase64);
        if (artifactRef != null) {
            fields.put("artifactRef", artifactRef);
        }
        fields.put("message", "Viewport attached as image media; inspect the screenshot, do not fetch artifactRef.");
        ToolResultPayload payload = ToolResultPayload.okData(getName(), fields);
        if (StringUtils.isNotBlank(imageBase64)) {
            payload.setBase64Image("data:image/png;base64," + imageBase64);
            payload.setImageMimeType("image/png");
        }
        return payload;
    }

    private String uploadPng(String imageBase64) {
        if (StringUtils.isBlank(imageBase64) || agentContext.getRuntimeDependencies() == null) {
            return null;
        }
        Path tmp = null;
        try {
            byte[] bytes = Base64.getDecoder().decode(imageBase64);
            tmp = Files.createTempFile("browser-viewport-", ".png");
            Files.write(tmp, bytes);
            FileArtifactPort fileArtifactPort = agentContext.getRuntimeDependencies().requireFileArtifactPort();
            String serviceUrl = agentContext.getRuntimeDependencies().requireReactorConfig().getCodeInterpreterUrl();
            FileRequest request = FileRequest.builder()
                    .requestId(StringUtils.defaultIfBlank(agentContext.getSessionId(), agentContext.getRequestId()))
                    .fileName("browser-viewport.png")
                    .description("browser viewport")
                    .localPath(tmp.toAbsolutePath().toString())
                    .build();
            FileResponse response = fileArtifactPort.register(serviceUrl, request);
            if (response == null) {
                return null;
            }
            ToolArtifactSource source = agentContext.getCurrentToolArtifactSource();
            if (source != null) {
                agentContext.registerGeneratedArtifact(source, File.builder()
                        .fileName("browser-viewport.png")
                        .ossUrl(response.getOssUrl())
                        .domainUrl(response.getDomainUrl())
                        .fileSize(response.getFileSize())
                        .description("browser viewport")
                        .isInternalFile(false)
                        .build());
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("url", null);
            payload.put("title", "viewport");
            payload.put("fileName", "browser-viewport.png");
            payload.put("domainUrl", response.getDomainUrl());
            payload.put("ossUrl", response.getOssUrl());
            if (agentContext.getPrinter() != null) {
                Map<String, Object> extra = new LinkedHashMap<>();
                extra.put("artifactRefs", java.util.List.of(Map.of(
                        "fileName", "browser-viewport.png",
                        "domainUrl", StringUtils.defaultString(response.getDomainUrl()),
                        "ossUrl", StringUtils.defaultString(response.getOssUrl()),
                        "previewUrl", StringUtils.defaultString(response.getDomainUrl(), response.getOssUrl())
                )));
                String messageId = source == null ? agentContext.getRequestId() : source.getToolCallId();
                agentContext.getPrinter().send(messageId, "browser_viewport", payload, extra, null, true);
            }
            return StringUtils.defaultIfBlank(response.getDomainUrl(), response.getOssUrl());
        } catch (Exception e) {
            log.warn("browser screenshot upload failed, requestId={}", agentContext.getRequestId(), e);
            return null;
        } finally {
            if (tmp != null) {
                try { Files.deleteIfExists(tmp); } catch (Exception ignore) { }
            }
        }
    }
}
