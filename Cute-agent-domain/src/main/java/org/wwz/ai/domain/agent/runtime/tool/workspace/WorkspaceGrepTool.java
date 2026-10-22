package org.wwz.ai.domain.agent.runtime.tool.workspace;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 工作区文本搜索。
 */
public class WorkspaceGrepTool extends AbstractWorkspacePathTool {

    public WorkspaceGrepTool(WorkspaceService workspaceService, WorkspaceRuntimeOptions workspaceRuntimeOptions) {
        super(workspaceService, workspaceRuntimeOptions);
    }

    @Override
    public String getName() {
        return "workspace_grep";
    }

    @Override
    public String getDescription() {
        return withWorkspaceHint("在会话工作区内搜索关键字或正则。path 可为文件或目录，缺省为工作区根。不要用 shell grep/rg 代替本工具。");
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("path", Map.of("type", "string", "description", "搜索起点（文件或目录）；缺省为工作区根"));
        properties.put("pattern", Map.of("type", "string", "description", "关键字或正则表达式"));
        properties.put("regex", Map.of("type", "boolean", "description", "是否按正则匹配，默认 false"));
        properties.put("case_sensitive", Map.of("type", "boolean", "description", "是否区分大小写，默认 false"));

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("pattern"));
        return parameters;
    }

    @Override
    public Object execute(Object input) {
        try {
            Map<String, Object> params = requireInputMap(input);
            Path basePath;
            Object pathValue = params.get("path");
            if (pathValue == null || String.valueOf(pathValue).isBlank()) {
                basePath = requireWorkspaceRoot();
            } else {
                basePath = requireAllowedPath(params);
            }
            Object patternValue = params.get("pattern");
            if (patternValue == null || String.valueOf(patternValue).isBlank()) {
                return failResult("pattern is required");
            }

            String searchPattern = String.valueOf(patternValue).trim();
            boolean regex = readBoolean(params, "regex", false);
            boolean caseSensitive = readBoolean(params, "case_sensitive", false);
            Pattern pattern = buildPattern(searchPattern, regex, caseSensitive);

            if (!Files.isRegularFile(basePath) && !Files.isDirectory(basePath)) {
                return failResult("workspace_grep 需要文件或目录路径: " + toAgentPath(basePath));
            }

            List<Map<String, Object>> matches = new ArrayList<>();
            boolean truncated = false;
            if (Files.isRegularFile(basePath)) {
                Path displayBasePath = basePath.getParent() == null ? basePath : basePath.getParent();
                truncated = grepFile(basePath, displayBasePath, pattern, matches);
            } else {
                int scanBudget = Math.max(1, workspaceRuntimeOptions.getMaxGrepMatches());
                try (var paths = Files.walk(basePath)) {
                    var iterator = paths.iterator();
                    int scanned = 0;
                    while (iterator.hasNext()) {
                        Path path = iterator.next();
                        if (path.equals(basePath)) {
                            continue;
                        }
                        if (scanned++ >= scanBudget) {
                            truncated = true;
                            break;
                        }
                        if (!Files.isRegularFile(path)) {
                            continue;
                        }
                        if (grepFile(path, basePath, pattern, matches)) {
                            truncated = true;
                            break;
                        }
                    }
                    if (!truncated && scanned >= scanBudget && iterator.hasNext()) {
                        truncated = true;
                    }
                }
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("path", toAgentPath(basePath));
            data.put("pattern", searchPattern);
            data.put("matches", matches);
            if (truncated) {
                data.put("truncated", Boolean.TRUE);
            }
            return okResult(data);
        } catch (WorkspaceAccessException e) {
            log.warn("{} workspace_grep failed, input={}", requestId(), input, e);
            return failResult(e.getMessage());
        } catch (IOException e) {
            log.error("{} workspace_grep io error, input={}", requestId(), input, e);
            return failResult("workspace_grep execute failed");
        } catch (Exception e) {
            log.error("{} workspace_grep error, input={}", requestId(), input, e);
            return failResult("workspace_grep execute failed");
        }
    }

    private Pattern buildPattern(String searchPattern, boolean regex, boolean caseSensitive) {
        String expression = regex ? searchPattern : Pattern.quote(searchPattern);
        int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
        return Pattern.compile(expression, flags);
    }

    private boolean grepFile(Path filePath, Path basePath, Pattern pattern,
                             List<Map<String, Object>> matches) {
        int maxMatches = Math.max(0, workspaceRuntimeOptions.getMaxGrepMatches());
        if (maxMatches == 0) {
            return true;
        }
        int maxLineChars = Math.max(1, workspaceRuntimeOptions.getMaxReadChars());
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(Files.newInputStream(filePath), StandardCharsets.UTF_8))) {
            int lineNumber = 0;
            int fileChars = 0;
            WorkspaceTextReader.Line line;
            while ((line = WorkspaceTextReader.readLineBounded(reader, maxLineChars)) != null) {
                lineNumber++;
                fileChars = Math.addExact(fileChars, line.characters());
                if (line.truncated() || fileChars > maxLineChars) {
                    return true;
                }
                if (pattern.matcher(line.text()).find()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("path", toRelativePath(basePath, filePath));
                    row.put("line", lineNumber);
                    row.put("text", line.text());
                    matches.add(row);
                    if (matches.size() >= maxMatches) {
                        return true;
                    }
                }
            }
        } catch (IOException ignore) {
            // A file can disappear or become unreadable while the directory is scanned.
        }
        return false;
    }
}
