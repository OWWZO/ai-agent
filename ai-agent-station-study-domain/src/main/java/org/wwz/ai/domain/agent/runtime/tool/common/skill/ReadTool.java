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

/**
 * 读取 skill 目录内文本文件。
 */
public class ReadTool extends AbstractSkillPathTool {

    public ReadTool(SkillRegistry skillRegistry, SkillRuntimeOptions skillRuntimeOptions) {
        super(skillRegistry, skillRuntimeOptions);
    }

    @Override
    public String getName() {
        return "read_tool";
    }

    @Override
    public String getDescription() {
        return "这是一个只读文件工具，用于读取已注册 skill 目录内的文本文件内容，支持按行截取。";
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("path", Map.of("type", "string", "description", "需要读取的绝对文件路径"));
        properties.put("start_line", Map.of("type", "integer", "description", "起始行号，默认从 1 开始"));
        properties.put("line_count", Map.of("type", "integer", "description", "读取的行数，默认 80"));

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
                return "read_tool 只支持读取文件路径";
            }

            int startLine = Math.max(1, readInt(params, "start_line", 1));
            int lineCount = Math.max(1, readInt(params, "line_count", 80));
            List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
            int fromIndex = Math.min(lines.size(), startLine - 1);
            int toIndex = Math.min(lines.size(), fromIndex + lineCount);

            StringBuilder result = new StringBuilder();
            result.append("路径：").append(filePath).append("\n");
            result.append("范围：").append(startLine).append(" - ").append(fromIndex + (toIndex - fromIndex)).append("\n");
            result.append("内容：\n");
            for (int i = fromIndex; i < toIndex; i++) {
                result.append(i + 1).append(" | ").append(lines.get(i)).append("\n");
            }

            if (result.length() > skillRuntimeOptions.getMaxReadChars()) {
                return result.substring(0, skillRuntimeOptions.getMaxReadChars())
                        + "\n[已截断，超过最大返回字符数 " + skillRuntimeOptions.getMaxReadChars() + "]";
            }
            return result.toString();
        } catch (SkillLoadException e) {
            log.warn("{} read_tool failed, input={}", requestId(), input, e);
            return e.getMessage();
        } catch (IOException e) {
            log.error("{} read_tool io error, input={}", requestId(), input, e);
            return "read_tool failed to read file";
        } catch (Exception e) {
            log.error("{} read_tool error, input={}", requestId(), input, e);
            return "read_tool execute failed";
        }
    }
}
