package org.wwz.ai.domain.agent.runtime.tool.workspace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 读取工作区文本文件（对齐 cchaha Read，含跨轮未变更去重 stub）。
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
                "读取会话工作区内的文本文件。path 可为绝对路径或相对工作区根的相对路径；"
                        + "默认从第 1 行起读取，可用 start_line/line_count 截取。"
                        + "若文件自上次同范围读取后未变化（含跨轮），将返回 unchanged stub，请复用更早的读取结果。"
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
            if (!Files.isRegularFile(filePath)) {
                return "workspace_read 只支持读取文件路径: " + filePath;
            }

            int startLine = Math.max(1, readInt(params, "start_line", 1));
            int lineCount = Math.max(1, readInt(params, "line_count", 2000));
            String absolutePath = filePath.toAbsolutePath().normalize().toString();
            long mtimeMs = Files.getLastModifiedTime(filePath).toMillis();
            String fullContent = Files.readString(filePath, StandardCharsets.UTF_8);
            String contentHash = WorkspaceReadStateStore.sha256Hex(fullContent);

            if (agentContext != null) {
                WorkspaceFileReadState existing = agentContext.getWorkspaceFileReadState(absolutePath);
                if (existing != null
                        && existing.getStartLine() == startLine
                        && existing.getLineCount() == lineCount
                        && isUnchanged(existing, mtimeMs, contentHash)) {
                    return FILE_UNCHANGED_STUB + "\n路径: " + filePath
                            + "\n范围: " + startLine + " + " + lineCount + " lines";
                }
            }

            List<String> lineList = Files.readAllLines(filePath, StandardCharsets.UTF_8);
            int fromIndex = Math.min(lineList.size(), startLine - 1);
            int toIndex = Math.min(lineList.size(), fromIndex + lineCount);

            StringBuilder result = new StringBuilder();
            result.append("路径: ").append(filePath).append('\n');
            result.append("范围: ").append(startLine).append(" - ").append(fromIndex + (toIndex - fromIndex)).append('\n');
            result.append("内容:\n");
            for (int i = fromIndex; i < toIndex; i++) {
                result.append(i + 1).append(" | ").append(lineList.get(i)).append('\n');
            }

            String body = result.toString();
            if (body.length() > workspaceRuntimeOptions.getMaxReadChars()) {
                body = body.substring(0, workspaceRuntimeOptions.getMaxReadChars())
                        + "\n[已截断，超过最大返回字符数 " + workspaceRuntimeOptions.getMaxReadChars() + "]";
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
            return body;
        } catch (WorkspaceAccessException e) {
            log.warn("{} workspace_read failed, input={}", requestId(), input, e);
            return e.getMessage();
        } catch (IOException e) {
            log.error("{} workspace_read io error, input={}", requestId(), input, e);
            return "workspace_read failed to read file";
        } catch (Exception e) {
            log.error("{} workspace_read error, input={}", requestId(), input, e);
            return "workspace_read execute failed";
        }
    }

    private boolean isUnchanged(WorkspaceFileReadState existing, long mtimeMs, String contentHash) {
        if (existing.getMtimeMs() == mtimeMs) {
            return true;
        }
        return existing.getContentHash() != null && existing.getContentHash().equals(contentHash);
    }
}
