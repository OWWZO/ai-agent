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

/**
 * 浏览 skill 目录结构。
 */
public class ListDirectoryTool extends AbstractSkillPathTool {

    public ListDirectoryTool(SkillRegistry skillRegistry, SkillRuntimeOptions skillRuntimeOptions) {
        super(skillRegistry, skillRuntimeOptions);
    }

    @Override
    public String getName() {
        return "list_directory_tool";
    }

    @Override
    public String getDescription() {
        return "这是一个目录浏览工具，用于列出已注册 skill 目录内的文件和子目录。";
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("path", Map.of("type", "string", "description", "要浏览的绝对目录路径"));
        properties.put("max_depth", Map.of("type", "integer", "description", "最大遍历深度，默认 2"));

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
            Path directoryPath = requireAllowedPath(params);
            if (!Files.isDirectory(directoryPath)) {
                return "list_directory_tool 只支持目录路径";
            }

            int maxDepth = Math.max(1, readInt(params, "max_depth", 2));
            StringBuilder result = new StringBuilder();
            result.append("目录：").append(directoryPath).append("\n");
            result.append("内容：\n");

            try (var pathStream = Files.walk(directoryPath, maxDepth)) {
                List<Path> entries = pathStream
                        .filter(path -> !path.equals(directoryPath))
                        .limit(skillRuntimeOptions.getMaxListEntries() + 1L)
                        .toList();
                boolean truncated = entries.size() > skillRuntimeOptions.getMaxListEntries();
                List<Path> displayEntries = truncated
                        ? entries.subList(0, skillRuntimeOptions.getMaxListEntries())
                        : entries;

                for (Path entry : displayEntries) {
                    String entryType = Files.isDirectory(entry) ? "DIR" : "FILE";
                    String relativePath = directoryPath.relativize(entry).toString().replace("\\", "/");
                    result.append("- [").append(entryType).append("] ").append(relativePath);
                    if (Files.isRegularFile(entry)) {
                        result.append(" (").append(Files.size(entry)).append(" bytes)");
                    }
                    result.append("\n");
                }
                if (truncated) {
                    result.append("[已截断，超过最大条数 ").append(skillRuntimeOptions.getMaxListEntries()).append("]\n");
                }
            }
            return result.toString();
        } catch (SkillLoadException e) {
            log.warn("{} list_directory_tool failed, input={}", requestId(), input, e);
            return e.getMessage();
        } catch (IOException e) {
            log.error("{} list_directory_tool io error, input={}", requestId(), input, e);
            return "list_directory_tool execute failed";
        } catch (Exception e) {
            log.error("{} list_directory_tool error, input={}", requestId(), input, e);
            return "list_directory_tool execute failed";
        }
    }
}
