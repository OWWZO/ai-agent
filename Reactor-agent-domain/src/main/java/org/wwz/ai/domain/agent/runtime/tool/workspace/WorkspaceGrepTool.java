package org.wwz.ai.domain.agent.runtime.tool.workspace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 工作区文本搜索（对齐 cchaha Grep）。
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
                return "pattern is required";
            }

            String searchPattern = String.valueOf(patternValue).trim();
            boolean regex = readBoolean(params, "regex", false);
            boolean caseSensitive = readBoolean(params, "case_sensitive", false);
            Pattern pattern = buildPattern(searchPattern, regex, caseSensitive);

            List<Path> candidateFiles;
            if (Files.isRegularFile(basePath)) {
                candidateFiles = List.of(basePath);
            } else if (Files.isDirectory(basePath)) {
                try (var pathStream = Files.walk(basePath)) {
                    candidateFiles = pathStream.filter(Files::isRegularFile).toList();
                }
            } else {
                return "workspace_grep 需要文件或目录路径: " + basePath;
            }

            StringBuilder result = new StringBuilder();
            result.append("路径: ").append(basePath).append('\n');
            result.append("匹配: ").append(searchPattern).append('\n');
            result.append("结果:\n");

            int matchCount = 0;
            for (Path filePath : candidateFiles) {
                List<String> lines;
                try {
                    lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
                } catch (IOException ignore) {
                    continue;
                }
                for (int i = 0; i < lines.size(); i++) {
                    if (pattern.matcher(lines.get(i)).find()) {
                        Path displayBasePath = Files.isDirectory(basePath) ? basePath : basePath.getParent();
                        String relativePath = displayBasePath == null
                                ? filePath.getFileName().toString()
                                : toRelativePath(displayBasePath, filePath);
                        result.append("- ")
                                .append(relativePath)
                                .append(':')
                                .append(i + 1)
                                .append(": ")
                                .append(lines.get(i))
                                .append('\n');
                        matchCount++;
                        if (matchCount >= workspaceRuntimeOptions.getMaxGrepMatches()) {
                            result.append("[已截断，超过最大匹配数 ")
                                    .append(workspaceRuntimeOptions.getMaxGrepMatches())
                                    .append("]\n");
                            return result.toString();
                        }
                    }
                }
            }
            return result.toString();
        } catch (WorkspaceAccessException e) {
            log.warn("{} workspace_grep failed, input={}", requestId(), input, e);
            return e.getMessage();
        } catch (IOException e) {
            log.error("{} workspace_grep io error, input={}", requestId(), input, e);
            return "workspace_grep execute failed";
        } catch (Exception e) {
            log.error("{} workspace_grep error, input={}", requestId(), input, e);
            return "workspace_grep execute failed";
        }
    }

    private Pattern buildPattern(String searchPattern, boolean regex, boolean caseSensitive) {
        String expression = regex ? searchPattern : Pattern.quote(searchPattern);
        int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
        return Pattern.compile(expression, flags);
    }
}
