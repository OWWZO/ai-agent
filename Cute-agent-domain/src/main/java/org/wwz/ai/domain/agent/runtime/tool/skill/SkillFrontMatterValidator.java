package org.wwz.ai.domain.agent.runtime.tool.skill;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * frontmatter 写回前的校验，参照 hermes-agent
 * {@code tools/skill_manager_tool.py::_validate_frontmatter}。
 * <p>
 * errors 表示必须阻断落盘的问题；warnings 只提示，不阻断，避免破坏存量 skill。
 */
public final class SkillFrontMatterValidator {

    /** 字段数量上限，超出仅告警。 */
    public static final int MAX_FIELDS = 64;

    /** 推荐的 skill 名规范；不符合只告警，不阻断。 */
    public static final Pattern RECOMMENDED_NAME_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9._-]*$");

    private SkillFrontMatterValidator() {
    }

    public record Result(List<String> errors, List<String> warnings) {

        public Result {
            errors = errors == null ? List.of() : List.copyOf(errors);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        public boolean isClean() {
            return errors.isEmpty() && warnings.isEmpty();
        }
    }

    /**
     * @param fields frontmatter 字段映射，可为 null（等价于空）
     */
    public static Result validate(Map<String, Object> fields) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (fields == null || fields.isEmpty()) {
            errors.add("frontmatter 不能为空");
            return new Result(errors, warnings);
        }

        String name = text(fields.get("name"));
        if (name == null) {
            errors.add("缺少必填字段 name");
        } else if (!RECOMMENDED_NAME_PATTERN.matcher(name).matches()) {
            warnings.add("name 建议使用小写字母、数字、点、下划线或连字符，且以字母或数字开头：" + name);
        }

        if (text(fields.get("description")) == null) {
            errors.add("缺少必填字段 description");
        }

        if (fields.size() > MAX_FIELDS) {
            warnings.add("frontmatter 字段过多（" + fields.size() + " > " + MAX_FIELDS + "）");
        }

        for (String key : List.of("allowed-tools", "allowed_tools", "tags", "platforms")) {
            Object value = fields.get(key);
            if (value == null) {
                continue;
            }
            if (value instanceof Collection<?>) {
                continue;
            }
            if (value instanceof String text && !text.isBlank()) {
                warnings.add(key + " 建议写成 YAML 列表，当前为字符串");
                continue;
            }
            warnings.add(key + " 应为字符串列表，当前类型：" + value.getClass().getSimpleName());
        }

        return new Result(errors, warnings);
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
