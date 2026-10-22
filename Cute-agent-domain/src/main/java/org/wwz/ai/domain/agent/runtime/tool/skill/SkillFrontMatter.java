package org.wwz.ai.domain.agent.runtime.tool.skill;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SKILL.md frontmatter 的结构化解析结果。
 * <p>
 * {@code rawText} 保留原始文本，供需要原样回写的场景使用；
 * {@code warnings} 记录降级解析等异常情况，供日志与 UI 提示。
 */
public record SkillFrontMatter(
        Map<String, Object> fields,
        String rawText,
        String body,
        boolean hasFrontMatter,
        List<String> warnings
) {

    public SkillFrontMatter {
        fields = fields == null ? new LinkedHashMap<>() : fields;
        rawText = rawText == null ? "" : rawText;
        body = body == null ? "" : body;
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    /**
     * 没有 frontmatter 时的结果：正文即全文。
     */
    public static SkillFrontMatter empty(String markdown) {
        return new SkillFrontMatter(
                new LinkedHashMap<>(), "", markdown == null ? "" : markdown, false, List.of());
    }

    /**
     * 取字符串字段；缺失或空白返回 null。
     */
    public String stringField(String key) {
        Object value = fields.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
