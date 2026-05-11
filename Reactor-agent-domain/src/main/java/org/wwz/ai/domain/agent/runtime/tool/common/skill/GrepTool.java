package org.wwz.ai.domain.agent.runtime.tool.common.skill;

import org.wwz.ai.domain.agent.runtime.tool.skill.SkillLoadException;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRegistry;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRuntimeOptions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 在 skill 目录内做文本搜索。
 */
public class GrepTool extends AbstractSkillPathTool {

    public GrepTool(SkillRegistry skillRegistry, SkillRuntimeOptions skillRuntimeOptions) {
        super(skillRegistry, skillRuntimeOptions);
    }

    @Override
    public String getName() {
        return "grep_tool";
    }

    @Override
    public String getDescription() {
        return "这是一个文本搜索工具，用于在已注册 skill 目录内搜索关键字或正则。";
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("path", Map.of("type", "string", "description", "搜索起点的绝对路径，可以是文件或目录"));
        properties.put("pattern", Map.of("type", "string", "description", "关键字或正则表达式"));
        properties.put("regex", Map.of("type", "boolean", "description", "是否按正则表达式匹配"));
        properties.put("case_sensitive", Map.of("type", "boolean", "description", "是否区分大小写"));

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
                return "grep_tool 需要文件或目录路径";
            }

            StringBuilder result = new StringBuilder();
            result.append("路径：").append(basePath).append("\n");
            result.append("匹配：").append(searchPattern).append("\n");
            result.append("结果：\n");

            int matchCount = 0;
            for (Path filePath : candidateFiles) {
                List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    if (pattern.matcher(lines.get(i)).find()) {
                        Path displayBasePath = Files.isDirectory(basePath) ? basePath : basePath.getParent();
                        String relativePath = displayBasePath == null
                                ? filePath.getFileName().toString()
                                : displayBasePath.relativize(filePath).toString().replace("\\", "/");
                        result.append("- ")
                                .append(relativePath)
                                .append(":")
                                .append(i + 1)
                                .append(": ")
                                .append(lines.get(i))
                                .append("\n");
                        matchCount++;
                        if (matchCount >= skillRuntimeOptions.getMaxGrepMatches()) {
                            result.append("[已截断，超过最大匹配数 ").append(skillRuntimeOptions.getMaxGrepMatches()).append("]\n");
                            return result.toString();
                        }
                    }
                }
            }
            return result.toString();
        } catch (SkillLoadException e) {
            log.warn("{} grep_tool failed, input={}", requestId(), input, e);
            return e.getMessage();
        } catch (IOException e) {
            log.error("{} grep_tool io error, input={}", requestId(), input, e);
            return "grep_tool execute failed";
        } catch (Exception e) {
            log.error("{} grep_tool error, input={}", requestId(), input, e);
            return "grep_tool execute failed";
        }
    }

    private Pattern buildPattern(String searchPattern, boolean regex, boolean caseSensitive) {
        String expression = regex ? searchPattern : Pattern.quote(searchPattern);
        int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
        return Pattern.compile(expression, flags);
    }
}
