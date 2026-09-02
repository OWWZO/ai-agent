package org.wwz.ai.domain.agent.runtime.tool.workspace;

import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 读取工作区文本文件（含跨轮未变更去重 stub）。
 */
public class WorkspaceReadTool extends AbstractWorkspacePathTool {

    public static final String FILE_UNCHANGED_STUB =
            "File unchanged since last read. The content from the earlier workspace_read tool_result "
                    + "in this conversation is still current — refer to that instead of re-reading.";

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
            String fullContent = Files.readString(filePath, StandardCharsets.UTF_8);
            String contentHash = WorkspaceReadStateStore.sha256Hex(fullContent);
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

            List<String> lineList = Files.readAllLines(filePath, StandardCharsets.UTF_8);
            int fromIndex = Math.min(lineList.size(), startLine - 1);
            int toIndex = Math.min(lineList.size(), fromIndex + lineCount);

            StringBuilder content = new StringBuilder();
            for (int i = fromIndex; i < toIndex; i++) {
                content.append(i + 1).append(" | ").append(lineList.get(i)).append('\n');
            }
            String body = content.toString();
            boolean truncated = false;
            if (body.length() > workspaceRuntimeOptions.getMaxReadChars()) {
                body = body.substring(0, workspaceRuntimeOptions.getMaxReadChars());
                truncated = true;
            }

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
            data.put("endLine", fromIndex + (toIndex - fromIndex));
            data.put("numLines", toIndex - fromIndex);
            data.put("totalLines", lineList.size());
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
        data.put("path", toAgentPath(filePath));
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
}
