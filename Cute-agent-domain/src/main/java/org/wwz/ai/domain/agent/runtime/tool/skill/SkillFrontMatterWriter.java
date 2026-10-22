package org.wwz.ai.domain.agent.runtime.tool.skill;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * frontmatter 序列化器：把字段映射还原成 {@code ---\n...\n---\n} 文本块。
 * <p>
 * <b>禁止用字符串拼接生成 frontmatter</b>——含 {@code ": "}、{@code " #"}、以特殊字符开头的
 * 值必须由 YAML dumper 自动加引号，否则重新解析时会报
 * {@code mapping values are not allowed here}。
 */
public final class SkillFrontMatterWriter {

    public static final String FENCE = "---";

    private SkillFrontMatterWriter() {
    }

    /**
     * 渲染 frontmatter 文本块，含首尾栅栏，以换行结尾。
     */
    public static String render(Map<String, Object> fields) {
        StringBuilder builder = new StringBuilder();
        builder.append(FENCE).append('\n');
        Map<String, Object> sanitized = withoutNullValues(fields);
        if (!sanitized.isEmpty()) {
            builder.append(newYaml().dump(sanitized));
        }
        builder.append(FENCE).append('\n');
        return builder.toString();
    }

    /**
     * 渲染完整 SKILL.md：frontmatter + 空行 + 正文，以换行结尾。
     */
    public static String renderDocument(Map<String, Object> fields, String body) {
        return render(fields) + "\n" + (body == null ? "" : body.strip()) + "\n";
    }

    private static Yaml newYaml() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        options.setIndicatorIndent(0);
        // 宽度设为最大值 + 关闭折行：否则长 description 会被 dumper 折成多行，
        // 后续按行解析的场景（以及人工阅读）都会出问题。
        options.setWidth(Integer.MAX_VALUE);
        options.setSplitLines(false);
        options.setAllowUnicode(true);
        return new Yaml(options);
    }

    private static Map<String, Object> withoutNullValues(Map<String, Object> fields) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (fields == null) {
            return out;
        }
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            out.put(entry.getKey(), entry.getValue());
        }
        return out;
    }
}
