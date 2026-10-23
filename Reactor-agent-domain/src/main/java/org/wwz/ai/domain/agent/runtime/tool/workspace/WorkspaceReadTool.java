package org.wwz.ai.domain.agent.runtime.tool.workspace;

import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 读取工作区文本文件（含跨轮未变更去重 stub）。
 */
public class WorkspaceReadTool extends AbstractWorkspacePathTool {

    public static final String FILE_UNCHANGED_STUB =
            "File unchanged since last read. The content from the earlier workspace_read tool_result "
                    + "in this conversation is still current — refer to that instead of re-reading.";

    /** 图片多模态读取默认上限：base64 后约 6.7MB，留足供应商请求体与 MEDIUMTEXT 的余量。 */
    public static final long DEFAULT_MAX_IMAGE_BYTES = 5L * 1024 * 1024;

    public WorkspaceReadTool(WorkspaceService workspaceService, WorkspaceRuntimeOptions workspaceRuntimeOptions) {
        super(workspaceService, workspaceRuntimeOptions);
    }

    @Override
    public String getName() {
        return "workspace_read";
    }

    @Override
    public String getDescription() {
        return withWorkspaceHint(
                "读取会话工作区内的文件。path 可为绝对路径或相对工作区根的相对路径。"
                        + "文本：默认从第 1 行起读取，可用 start_line/line_count 截取；"
                        + "若文件自上次同范围读取后未变化（含跨轮），将返回 unchanged stub，请复用更早的读取结果。"
                        + "图片（png/jpg/gif/webp 等）：返回短元数据，图片本体作为多模态内容供模型阅读。"
                        + "不要用 shell cat/head 代替本工具。"
        );
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("path", Map.of("type", "string", "description", "文件路径（绝对或相对工作区根）"));
        properties.put("start_line", Map.of("type", "integer", "description", "起始行号，默认 1"));
        properties.put("line_count", Map.of("type", "integer", "description", "读取行数，默认 2000"));

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("path"));
        return parameters;
    }

    @Override
    public Object execute(Object input) {
        try {
            Map<String, Object> params = requireInputMap(input);
            Path filePath = requireAllowedPath(params);
            if (!isReadableFile(filePath)) {
                return failResult("workspace_read 只支持读取文件路径: " + toAgentPath(filePath));
            }

            if (isImage(filePath)) {
                // 图片不走文本截断；返回文件内容引用，文本读取状态仅用于后续可编辑文件的并发检查。
                return readImage(filePath);
            }

            int startLine = Math.max(1, readInt(params, "start_line", readInt(params, "offset", 1)));
            int lineCount = Math.max(1, readInt(params, "line_count", readInt(params, "limit", 2000)));
            String absolutePath = filePath.toAbsolutePath().normalize().toString();
            long mtimeMs = Files.getLastModifiedTime(filePath).toMillis();
            ReadSnapshot snapshot = readSnapshot(filePath, startLine, lineCount,
                    workspaceRuntimeOptions.getMaxReadChars());
            String contentHash = snapshot.contentHash();
            // 读取状态绑定 mtime、范围和内容 hash，workspace_edit 据此拒绝基于过期内容的覆盖写。

            if (agentContext != null) {
                WorkspaceFileReadState existing = agentContext.getWorkspaceFileReadState(absolutePath);
                if (existing != null
                        && existing.getStartLine() == startLine
                        && existing.getLineCount() == lineCount
                        && isUnchanged(existing, mtimeMs, contentHash)) {
                    String agentPath = toAgentPath(filePath);
                    Map<String, Object> unchanged = new LinkedHashMap<>();
                    unchanged.put("type", "file_unchanged");
                    unchanged.put("unchanged", Boolean.TRUE);
                    unchanged.put("path", agentPath);
                    unchanged.put("startLine", startLine);
                    unchanged.put("lineCount", lineCount);
                    unchanged.put("message", FILE_UNCHANGED_STUB);
                    return okResult(unchanged);
                }
            }

            String body = snapshot.body();
            boolean truncated = snapshot.truncated();

            if (agentContext != null) {
                agentContext.markWorkspaceFileRead(WorkspaceFileReadState.builder()
                        .absolutePath(absolutePath)
                        .mtimeMs(mtimeMs)
                        .startLine(startLine)
                        .lineCount(lineCount)
                        .contentHash(contentHash)
                        .build());
            }
            String agentPath = toAgentPath(filePath);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("type", "text");
            data.put("path", agentPath);
            data.put("startLine", startLine);
            data.put("endLine", snapshot.endLine());
            data.put("numLines", snapshot.numLines());
            data.put("totalLines", snapshot.totalLines());
            data.put("content", body);
            if (truncated) {
                data.put("truncated", Boolean.TRUE);
            }
            return okResult(data);
        } catch (WorkspaceAccessException e) {
            log.warn("{} workspace_read failed, input={}", requestId(), input, e);
            return failResult(e.getMessage());
        } catch (IOException e) {
            log.error("{} workspace_read io error, input={}", requestId(), input, e);
            return failResult("workspace_read failed to read file");
        } catch (Exception e) {
            log.error("{} workspace_read error, input={}", requestId(), input, e);
            return failResult("workspace_read execute failed");
        }
    }

    private ToolResultPayload readImage(Path filePath) throws IOException {
        long size = Files.size(filePath);
        String agentPath = toAgentPath(filePath);
        // 图片走多模态而不是文本截断，所以体积必须自己把关：整图 base64 会放大 4/3，
        // 供应商请求体上限和 working_memory_message.base64_image（MEDIUMTEXT）都扛不住超大图。
        if (size > maxImageBytes()) {
            return imageTooLarge(agentPath, size);
        }
        byte[] bytes = Files.readAllBytes(filePath);
        String mimeType = Files.probeContentType(filePath);
        if (mimeType == null || !mimeType.startsWith("image/")) {
            mimeType = switch (extension(filePath)) {
                case "jpg", "jpeg" -> "image/jpeg";
                case "gif" -> "image/gif";
                case "webp" -> "image/webp";
                default -> "image/png";
            };
        }
        // observation 只保留元数据；整图走 base64Image，由 DomainMessageConverter 转成 Spring AI Media。
        String dataUrl = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "image");
        data.put("path", agentPath);
        data.put("mimeType", mimeType);
        data.put("size", bytes.length);
        data.put("message", "Image loaded as multimodal content; inspect the attached image media.");
        return ToolResultPayload.builder()
                .llmData(data)
                .base64Image(dataUrl)
                .imageMimeType(mimeType)
                .failed(Boolean.FALSE)
                .build();
    }

    private long maxImageBytes() {
        long configured = workspaceRuntimeOptions.getMaxImageBytes();
        return configured > 0 ? configured : DEFAULT_MAX_IMAGE_BYTES;
    }

    /**
     * 超大图不加载成多模态，只回元数据 + 可执行的下一步。
     * 直接失败会诱使模型反复重读同一张图，所以这里用 ok=false 的结构化结果说明原因。
     */
    private ToolResultPayload imageTooLarge(String agentPath, long size) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "image_too_large");
        data.put("path", agentPath);
        data.put("size", size);
        data.put("maxBytes", maxImageBytes());
        data.put("message", "图片超过多模态读取上限（" + size + " > " + maxImageBytes()
                + " 字节），未作为图片加载。请改用更小的截图范围（例如 browser screenshot --ref 只截目标元素），"
                + "或让上游把图片缩小后再读。不要重复读取同一路径。");
        return ToolResultPayload.softFailData(getName(), data);
    }

    private boolean isImage(Path filePath) {
        return switch (extension(filePath)) {
            case "png", "jpg", "jpeg", "gif", "webp", "bmp", "tiff", "tif" -> true;
            default -> false;
        };
    }

    private boolean isReadableFile(Path filePath) {
        if (Files.isRegularFile(filePath)) {
            return true;
        }
        // Windows 某些会话目录的文件属性查询可能无法识别常规文件；存在且非目录时仍允许 UTF-8 读取。
        return System.getProperty("os.name", "").startsWith("Windows")
                && Files.exists(filePath)
                && !Files.isDirectory(filePath);
    }

    private String extension(Path filePath) {
        String name = filePath.getFileName().toString().toLowerCase();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1);
    }

    private boolean isUnchanged(WorkspaceFileReadState existing, long mtimeMs, String contentHash) {
        if (existing.getMtimeMs() == mtimeMs) {
            return true;
        }
        return existing.getContentHash() != null && existing.getContentHash().equals(contentHash);
    }

    private ReadSnapshot readSnapshot(Path filePath, int startLine, int lineCount, int maxChars)
            throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IOException("SHA-256 unavailable", e);
        }
        int outputLimit = Math.max(1, maxChars);
        StringBuilder body = new StringBuilder(Math.min(outputLimit, 1024));
        int totalLines = 0;
        int endLine = Math.max(0, startLine - 1);
        boolean truncated = false;
        long endExclusive = (long) startLine + lineCount;
        try (InputStream input = new java.security.DigestInputStream(
                new BufferedInputStream(Files.newInputStream(filePath)), digest);
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            WorkspaceTextReader.Line line;
            while ((line = WorkspaceTextReader.readLine(reader, outputLimit)) != null) {
                totalLines++;
                if (totalLines >= startLine && totalLines < endExclusive) {
                    String rendered = (totalLines + " | " + line.text() + "\n");
                    int remaining = outputLimit - body.length();
                    if (remaining > 0) {
                        body.append(rendered, 0, Math.min(remaining, rendered.length()));
                    }
                    if (rendered.length() > remaining || line.truncated()) {
                        truncated = true;
                    }
                    endLine = totalLines;
                }
            }
        }
        int numLines = totalLines < startLine
                ? 0
                : (int) (Math.min((long) totalLines, endExclusive - 1) - startLine + 1);
        return new ReadSnapshot(body.toString(), HexFormat.of().formatHex(digest.digest()),
                endLine, numLines,
                totalLines, truncated);
    }

    private record ReadSnapshot(String body, String contentHash, int endLine, int numLines,
                                int totalLines, boolean truncated) {
    }
}
