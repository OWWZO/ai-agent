package org.wwz.ai.domain.agent.runtime.tool.common.skill;

import org.wwz.ai.domain.agent.runtime.tool.skill.SkillLoadException;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRegistry;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRuntimeOptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 在 skill 根目录内做 glob 匹配。
 */
public class GlobTool extends AbstractSkillPathTool {

    public GlobTool(SkillRegistry skillRegistry, SkillRuntimeOptions skillRuntimeOptions) {
        super(skillRegistry, skillRuntimeOptions);
    }

    @Override
    public String getName() {
        return "glob_tool";
    }

    @Override
    public String getDescription() {
        return "这是一个文件匹配工具，用于在已注册 skill 目录内按 glob 规则搜索文件。";
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("path", Map.of("type", "string", "description", "搜索起点目录的绝对路径"));
        properties.put("pattern", Map.of("type", "string", "description", "glob 匹配模式，例如 references/**/*.md"));

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("path", "pattern"));
        return parameters;
    }

    @Override
    public Object execute(Object input) {
        try {
            Map<String, Object> params = requireInputMap(input);
            Path basePath = requireAllowedPath(params);
            if (!Files.isDirectory(basePath)) {
                return "glob_tool 只支持目录路径";
            }
            Object patternValue = params.get("pattern");
            if (patternValue == null || String.valueOf(patternValue).isBlank()) {
                return "pattern is required";
            }

            String pattern = String.valueOf(patternValue).trim();
            Pattern matcher = buildGlobPattern(pattern);
            StringBuilder result = new StringBuilder();
            result.append("目录：").append(basePath).append("\n");
            result.append("模式：").append(pattern).append("\n");
            result.append("命中：\n");

            try (var pathStream = Files.walk(basePath)) {
                List<Path> matchedPaths = pathStream
                        .filter(Files::isRegularFile)
                        .filter(path -> matcher.matcher(toRelativePath(basePath, path)).matches())
                        .limit(skillRuntimeOptions.getMaxGlobResults() + 1L)
                        .toList();
                boolean truncated = matchedPaths.size() > skillRuntimeOptions.getMaxGlobResults();
                List<Path> displayPaths = truncated
                        ? matchedPaths.subList(0, skillRuntimeOptions.getMaxGlobResults())
                        : matchedPaths;

                for (Path matchedPath : displayPaths) {
                    result.append("- ")
                            .append(basePath.relativize(matchedPath).toString().replace("\\", "/"))
                            .append("\n");
                }
                if (truncated) {
                    result.append("[已截断，超过最大匹配数 ").append(skillRuntimeOptions.getMaxGlobResults()).append("]\n");
                }
            }
            return result.toString();
        } catch (SkillLoadException e) {
            log.warn("{} glob_tool failed, input={}", requestId(), input, e);
            return e.getMessage();
        } catch (IOException e) {
            log.error("{} glob_tool io error, input={}", requestId(), input, e);
            return "glob_tool execute failed";
        } catch (Exception e) {
            log.error("{} glob_tool error, input={}", requestId(), input, e);
            return "glob_tool execute failed";
        }
    }

    /**
     * 使用统一的 `/` 路径语义做 glob 匹配，避免 Windows 下分隔符差异导致主流 skill 模式失效。
     */
    private Pattern buildGlobPattern(String pattern) {
        String normalized = pattern.replace("\\", "/");
        StringBuilder regex = new StringBuilder("^");
        for (int index = 0; index < normalized.length(); index++) {
            char currentChar = normalized.charAt(index);
            if (currentChar == '*') {
                boolean doubleStar = index + 1 < normalized.length() && normalized.charAt(index + 1) == '*';
                if (doubleStar) {
                    boolean followedBySlash = index + 2 < normalized.length() && normalized.charAt(index + 2) == '/';
                    if (followedBySlash) {
                        regex.append("(?:.*/)?");
                        index++;
                    } else {
                        regex.append(".*");
                    }
                    index++;
                } else {
                    regex.append("[^/]*");
                }
            } else if (currentChar == '?') {
                regex.append("[^/]");
            } else if ("\\.[]{}()+-^$|".indexOf(currentChar) >= 0) {
                regex.append("\\").append(currentChar);
            } else {
                regex.append(currentChar);
            }
        }
        regex.append("$");
        return Pattern.compile(regex.toString());
    }

    private String toRelativePath(Path basePath, Path filePath) {
        return basePath.relativize(filePath).toString().replace("\\", "/");
    }
}
