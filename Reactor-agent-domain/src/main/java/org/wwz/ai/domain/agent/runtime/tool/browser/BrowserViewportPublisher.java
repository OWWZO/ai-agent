package org.wwz.ai.domain.agent.runtime.tool.browser;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.adapter.port.FileArtifactPort;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.dto.File;
import org.wwz.ai.domain.agent.runtime.dto.FileRequest;
import org.wwz.ai.domain.agent.runtime.dto.FileResponse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public final class BrowserViewportPublisher {

    private BrowserViewportPublisher() {
    }

    public static void emitTree(AgentContext agentContext, Map<String, Object> fields) {
        if (agentContext == null || agentContext.getPrinter() == null || fields == null || fields.isEmpty()) {
            return;
        }
        if (fields.get("tree") == null && fields.get("url") == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("url", fields.get("url"));
        payload.put("title", fields.get("title"));
        Object tree = fields.get("tree");
        if (tree instanceof String text) {
            payload.put("tree", text.length() > 4000 ? text.substring(0, 4000) : text);
        }
        String messageId = agentContext.getCurrentToolArtifactSource() == null
                ? agentContext.getRequestId()
                : agentContext.getCurrentToolArtifactSource().getToolCallId();
        agentContext.getPrinter().send(messageId, "browser_viewport", payload, true);
    }

    public static String uploadPng(AgentContext agentContext, String imageBase64) {
        if (agentContext == null || agentContext.getRuntimeDependencies() == null || StringUtils.isBlank(imageBase64)) {
            return null;
        }
        Path tmp = null;
        try {
            byte[] bytes = Base64.getDecoder().decode(imageBase64.replaceAll("\\s", ""));
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
                extra.put("artifactRefs", List.of(Map.of(
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
                try {
                    Files.deleteIfExists(tmp);
                } catch (Exception ignore) {
                }
            }
        }
    }
}
