package org.wwz.ai.domain.agent.runtime.tool.common.canvas;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.adapter.port.FileArtifactPort;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.CanvasPublishToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolFileRefMapper;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.dto.CodeInterpreterResponse;
import org.wwz.ai.domain.agent.runtime.dto.File;
import org.wwz.ai.domain.agent.runtime.dto.FileRequest;
import org.wwz.ai.domain.agent.runtime.dto.FileResponse;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspacePaths;
import org.wwz.ai.domain.agent.runtime.util.StringUtil;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Publish HTML canvas into the session file service for right-panel preview.
 * P0: mode=html only; storage via existing file service (decision 1.A).
 */
@Slf4j
@Data
public class CanvasPublishTool implements BaseTool {

    /** Soft inline budget (~20KB); larger HTML should use html_path. */
    private static final int SOFT_INLINE_HTML_BYTES = 20_480;

    private AgentContext agentContext;

    @Override
    public String getName() {
        return CanvasToolNames.CANVAS_PUBLISH;
    }

    @Override
    public String getDescription() {
        return "Publish an HTML canvas document into the workspace preview panel. "
                + "Use ONLY when the user explicitly wants HTML / a webpage / printable report, "
                + "or page-scale layout that cannot be expressed by GenUI components. "
                + "Charts / KPI / dashboards / multi-card UI → prefer emit_ui_tree (not this tool). "
                + "Prefer this over report_tool for HTML deliverables. mode=html only. "
                + "Payload: (1) compact html ≲ ~20KB → inline `html` (prefer body fragment so host "
                + "Tailwind/Inter/wa-* shell applies); "
                + "(2) larger → write file first (workspace_write/file_tool) then `html_path`. "
                + "Inline scripts and on* handlers are stored; preview opens with JS enabled "
                + "(CSP allows scripts). Call get_html_canvas_guide for substantial pages. "
                + "Escape double quotes as \\\" and newlines as \\n in inline html.";
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> title = new HashMap<>();
        title.put("type", "string");
        title.put("description", "Canvas title shown to the user.");

        Map<String, Object> mode = new HashMap<>();
        mode.put("type", "string");
        mode.put("enum", List.of("html"));
        mode.put("description", "Publish mode. P0 only supports html.");

        Map<String, Object> html = new HashMap<>();
        html.put("type", "string");
        html.put("description",
                "Full HTML document or body fragment for mode=html when ≲ ~20KB. "
                        + "Larger pages: write file then pass html_path.");

        Map<String, Object> htmlPath = new HashMap<>();
        htmlPath.put("type", "string");
        htmlPath.put("description",
                "Relative path under the active workspace (or session product file name) "
                        + "containing UTF-8 HTML. Preferred for large pages.");

        Map<String, Object> filename = new HashMap<>();
        filename.put("type", "string");
        filename.put("description", "Optional output file name ending with .html/.htm.");

        Map<String, Object> openInPanel = new HashMap<>();
        openInPanel.put("type", "boolean");
        openInPanel.put("description", "Whether to notify the UI to open the preview panel. Default true.");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("title", title);
        properties.put("mode", mode);
        properties.put("html", html);
        properties.put("html_path", htmlPath);
        properties.put("filename", filename);
        properties.put("open_in_panel", openInPanel);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("title", "mode"));
        return parameters;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Object input) {
        try {
            Map<String, Object> params = input instanceof Map<?, ?> map
                    ? castMap(map)
                    : Map.of();
            String title = stringVal(params.get("title"));
            String mode = stringVal(params.get("mode"));
            if (StringUtils.isBlank(mode)) {
                mode = "html";
            }
            if (!"html".equalsIgnoreCase(mode)) {
                return failure(title, mode, "P0 only supports mode=html. Got: " + mode);
            }
            if (StringUtils.isBlank(title)) {
                title = "Canvas";
            }

            boolean salvaged = Boolean.TRUE.equals(params.get("__salvaged"));
            String html = stringVal(params.get("html"));
            String htmlPath = stringVal(params.get("html_path"));
            String filename = stringVal(params.get("filename"));
            boolean openInPanel = params.get("open_in_panel") == null
                    || Boolean.TRUE.equals(params.get("open_in_panel"))
                    || "true".equalsIgnoreCase(String.valueOf(params.get("open_in_panel")));

            if (StringUtils.isBlank(html) && StringUtils.isBlank(htmlPath)) {
                return failure(title, mode,
                        "Provide either `html` (inline) or `html_path` (file under workspace/session).");
            }

            if (StringUtils.isNotBlank(htmlPath)) {
                String loaded = loadHtmlFromPath(htmlPath);
                if (loaded == null) {
                    return failure(title, mode, "html_path not found or unreadable: " + htmlPath);
                }
                html = loaded;
                if (StringUtils.isBlank(filename)) {
                    filename = Path.of(htmlPath.replace('\\', '/')).getFileName().toString();
                }
            }

            // LeAgent-aligned: keep scripts (allowJs=true); inject host shell for bare pages.
            html = HtmlPreviewSanitizer.buildPreviewHtml(html, true);

            int htmlBytes = html.getBytes(StandardCharsets.UTF_8).length;
            if (StringUtils.isBlank(htmlPath) && htmlBytes > SOFT_INLINE_HTML_BYTES && !salvaged) {
                log.warn("{} canvas_publish large inline html bytes={} (soft budget={})",
                        agentContext.getRequestId(), htmlBytes, SOFT_INLINE_HTML_BYTES);
            }

            String uploadName = resolveUploadFileName(filename, title);
            ToolArtifactSource artifactSource = agentContext.requireCurrentToolArtifactSource(getName());
            return uploadAndNotify(title, mode, html, uploadName, openInPanel, salvaged, artifactSource);
        } catch (Exception e) {
            log.error("{} canvas_publish error", agentContext == null ? "-" : agentContext.getRequestId(), e);
            return failure(null, "html", "canvas_publish failed: " + e.getMessage());
        }
    }

    private ToolResultPayload uploadAndNotify(String title,
                                              String mode,
                                              String html,
                                              String uploadName,
                                              boolean openInPanel,
                                              boolean salvaged,
                                              ToolArtifactSource artifactSource) {
        ReactorConfig reactorConfig = requireReactorConfig();
        FileArtifactPort fileArtifactPort = requireFileArtifactPort();

        FileRequest fileRequest = FileRequest.builder()
                .requestId(agentContext.getSessionId())
                .fileName(uploadName)
                .description(StringUtils.left(StringUtils.defaultIfBlank(title, "Canvas HTML"), 80))
                .content(html)
                .build();
        fileRequest.setFileName(StringUtil.removeSpecialChars(fileRequest.getFileName()));
        if (StringUtils.isBlank(fileRequest.getFileName())) {
            fileRequest.setFileName("canvas.html");
        }
        if (!fileRequest.getFileName().toLowerCase().endsWith(".html")
                && !fileRequest.getFileName().toLowerCase().endsWith(".htm")) {
            fileRequest.setFileName(fileRequest.getFileName() + ".html");
        }

        log.info("{} canvas_publish upload name={} salvaged={} bytes={}",
                agentContext.getRequestId(),
                fileRequest.getFileName(),
                salvaged,
                html.getBytes(StandardCharsets.UTF_8).length);

        FileResponse fileResponse;
        try {
            fileResponse = fileArtifactPort.upload(reactorConfig.getCodeInterpreterUrl(), fileRequest);
        } catch (Exception e) {
            log.error("{} canvas_publish upload error", agentContext.getRequestId(), e);
            return failure(title, mode, "Upload failed for " + fileRequest.getFileName() + ": " + e.getMessage());
        }
        if (fileResponse == null) {
            return failure(title, mode, "Upload failed for " + fileRequest.getFileName());
        }

        List<CodeInterpreterResponse.FileInfo> fileInfo = new ArrayList<>();
        fileInfo.add(CodeInterpreterResponse.FileInfo.builder()
                .fileName(fileRequest.getFileName())
                .ossUrl(fileResponse.getOssUrl())
                .domainUrl(fileResponse.getDomainUrl())
                .fileSize(fileResponse.getFileSize())
                .build());

        File file = File.builder()
                .ossUrl(fileResponse.getOssUrl())
                .domainUrl(fileResponse.getDomainUrl())
                .fileName(fileRequest.getFileName())
                .fileSize(fileResponse.getFileSize())
                .description(title)
                .isInternalFile(false)
                .build();
        agentContext.registerGeneratedArtifact(artifactSource, file);

        if (openInPanel && agentContext.getPrinter() != null) {
            String toolCallId = artifactSource == null ? null : artifactSource.getToolCallId();
            CodeInterpreterResponse htmlResponse = CodeInterpreterResponse.builder()
                    .isFinal(true)
                    .toolCallId(toolCallId)
                    .fileInfo(fileInfo)
                    .data(title)
                    .codeOutput(title)
                    .build();
            String digitalEmployee = agentContext.getToolCollection() == null
                    ? null
                    : agentContext.getToolCollection().getDigitalEmployee(getName());
            // Align with report_tool(fileType=html) so ActionView/HTMLRenderer opens the preview.
            agentContext.getPrinter().send(toolCallId, "html", htmlResponse, digitalEmployee, true);
        }

        String preview = StringUtils.defaultIfBlank(fileResponse.getDomainUrl(), fileResponse.getOssUrl());
        String download = StringUtils.defaultIfBlank(fileResponse.getOssUrl(), fileResponse.getDomainUrl());
        CanvasPublishToolOutput structuredOutput = CanvasPublishToolOutput.builder()
                .title(title)
                .mode(mode)
                .primaryFileName(fileRequest.getFileName())
                .previewUrl(preview)
                .downloadUrl(download)
                .openInPanel(openInPanel)
                .salvaged(salvaged)
                .fileRefs(ToolFileRefMapper.fromCodeInterpreterFileInfo(fileInfo))
                .build();
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("message", "Canvas published. Prefer the HTML preview panel; do not call report_tool for the same page.");
        fields.put("title", title);
        fields.put("mode", mode);
        fields.put("fileName", fileRequest.getFileName());
        fields.put("previewUrl", preview);
        fields.put("downloadUrl", download);
        fields.put("openInPanel", openInPanel);
        fields.put("salvaged", salvaged);
        return ToolResultPayload.okData(getName(), fields, structuredOutput);
    }

    private String loadHtmlFromPath(String htmlPath) {
        if (StringUtils.isBlank(htmlPath) || agentContext == null || StringUtils.isBlank(agentContext.getSessionId())) {
            return null;
        }
        String normalized = htmlPath.trim().replace('\\', '/');
        if (normalized.contains("..")) {
            return null;
        }
        try {
            Path sessionRoot = WorkspacePaths.skillOutputSessionRoot(agentContext.getSessionId());
            Path candidate;
            Path asPath = Path.of(normalized);
            if (asPath.isAbsolute()) {
                candidate = asPath.normalize();
                if (!candidate.startsWith(sessionRoot)) {
                    return null;
                }
            } else {
                candidate = sessionRoot.resolve(normalized).normalize();
                if (!candidate.startsWith(sessionRoot)) {
                    return null;
                }
            }
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.warn("{} canvas_publish html_path read failed path={}", agentContext.getRequestId(), htmlPath, e);
        }
        return null;
    }

    private String resolveUploadFileName(String filename, String title) {
        if (StringUtils.isNotBlank(filename)) {
            return filename.trim();
        }
        String base = StringUtils.defaultIfBlank(title, "canvas")
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .trim();
        if (base.isEmpty()) {
            base = "canvas";
        }
        if (base.length() > 80) {
            base = base.substring(0, 80);
        }
        if (!base.toLowerCase().endsWith(".html") && !base.toLowerCase().endsWith(".htm")) {
            base = base + ".html";
        }
        return base;
    }

    private ToolResultPayload failure(String title, String mode, String message) {
        return ToolResultPayload.failure(
                message,
                message,
                CanvasPublishToolOutput.builder()
                        .title(title)
                        .mode(mode)
                        .build(),
                message
        );
    }

    private Map<String, Object> castMap(Map<?, ?> map) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (e.getKey() != null) {
                out.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
        return out;
    }

    private String stringVal(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    private ReactorConfig requireReactorConfig() {
        if (agentContext == null || agentContext.getRuntimeDependencies() == null) {
            throw new IllegalStateException("CanvasPublishTool missing ReactorRuntimeDependencies");
        }
        return agentContext.getRuntimeDependencies().requireReactorConfig();
    }

    private FileArtifactPort requireFileArtifactPort() {
        if (agentContext == null || agentContext.getRuntimeDependencies() == null) {
            throw new IllegalStateException("CanvasPublishTool missing ReactorRuntimeDependencies");
        }
        return agentContext.getRuntimeDependencies().requireFileArtifactPort();
    }
}
