package org.wwz.ai.domain.agent.runtime.tool.workspace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 列出工作区目录（对齐 cchaha 用工具浏览而非裸 ls 的思路）。
 */
public class WorkspaceListTool extends AbstractWorkspacePathTool {

    public WorkspaceListTool(WorkspaceService workspaceService, WorkspaceRuntimeOptions workspaceRuntimeOptions) {
        super(workspaceService, workspaceRuntimeOptions);
    }

    @Override
    public String getName() {
        return "workspace_list";
    }

    @Override
    public String getDescription() {
        return withWorkspaceHint("列出会话工作区内的文件和子目录。path 默认可为工作区根或其下目录；可用 max_depth 控制遍历深度。");
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("path", Map.of("type", "string", "description", "目录路径（绝对或相对工作区根）；缺省为工作区根"));
        properties.put("max_depth", Map.of("type", "integer", "description", "最大遍历深度，默认 2"));

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of());
        return parameters;
    }

    @Override
    public Object execute(Object input) {
        try {
            Map<String, Object> params = requireInputMap(input == null ? Map.of() : input);
            Path directoryPath;
            Object pathValue = params.get("path");
            if (pathValue == null || String.valueOf(pathValue).isBlank()) {
                directoryPath = requireWorkspaceRoot();
            } else {
                directoryPath = requireAllowedPath(params);
            }
            if (!Files.isDirectory(directoryPath)) {
                return failResult("workspace_list 只支持目录路径: " + directoryPath);
            }

            int maxDepth = Math.max(1, readInt(params, "max_depth", 2));
            List<Map<String, Object>> entriesOut = new ArrayList<>();
            boolean truncated = false;
            try (var pathStream = Files.walk(directoryPath, maxDepth)) {
                List<Path> entries = pathStream
                        .filter(path -> !path.equals(directoryPath))
                        .limit(workspaceRuntimeOptions.getMaxListEntries() + 1L)
                        .toList();
                truncated = entries.size() > workspaceRuntimeOptions.getMaxListEntries();
                List<Path> displayEntries = truncated
                        ? entries.subList(0, workspaceRuntimeOptions.getMaxListEntries())
                        : entries;

                for (Path entry : displayEntries) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("type", Files.isDirectory(entry) ? "DIR" : "FILE");
                    row.put("path", toRelativePath(directoryPath, entry));
                    if (Files.isRegularFile(entry)) {
                        row.put("bytes", Files.size(entry));
                    }
                    entriesOut.add(row);
                }
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("path", directoryPath.toString());
            data.put("entries", entriesOut);
            if (truncated) {
                data.put("truncated", Boolean.TRUE);
            }
            return okResult(data);
        } catch (WorkspaceAccessException e) {
            log.warn("{} workspace_list failed, input={}", requestId(), input, e);
            return failResult(e.getMessage());
        } catch (IOException e) {
            log.error("{} workspace_list io error, input={}", requestId(), input, e);
            return failResult("workspace_list execute failed");
        } catch (Exception e) {
            log.error("{} workspace_list error, input={}", requestId(), input, e);
            return failResult("workspace_list execute failed");
        }
    }
}
