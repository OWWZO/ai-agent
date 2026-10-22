package org.wwz.ai.domain.agent.runtime.tool.skill;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SKILL.md frontmatter 的<b>唯一</b>解析入口。
 * <p>
 * 参照 hermes-agent {@code agent/skill_utils.py::parse_frontmatter} 实现，行为契约：
 * <ol>
 *   <li>仅剥离一个前导 BOM（U+FEFF），中间出现的 BOM 视为数据；</li>
 *   <li>首行必须是 {@code ---}，结束栅栏为行首 {@code ---}；缺任一栅栏则视为无 frontmatter，正文即全文；</li>
 *   <li>用 snakeyaml {@link SafeConstructor} 解析，值<b>原样保留</b>（不剥引号、不做类型转换）；</li>
 *   <li>解析失败或结果不是 Map 时降级为逐行 {@code key: value} 解析，并写入 warnings；</li>
 *   <li>不抛业务异常（除入参为 null）。</li>
 * </ol>
 * 本类为无状态工具类，静态方法调用，不依赖 Spring 容器。
 */
public final class SkillFrontMatterParser {

    private static final String BOM = "\uFEFF";
    private static final String FENCE = "---";

    private SkillFrontMatterParser() {
    }

    /**
     * 解析 markdown 全文，返回 frontmatter 与正文。
     *
     * @param markdown SKILL.md 全文，不可为 null
     */
    public static SkillFrontMatter parse(String markdown) {
        if (markdown == null) {
            throw new IllegalArgumentException("markdown must not be null");
        }
        String text = stripBom(markdown);
        if (text.isEmpty()) {
            return SkillFrontMatter.empty(text);
        }

        int firstLineBreak = indexOfLineBreak(text, 0);
        String firstLine = firstLineBreak < 0 ? text : text.substring(0, firstLineBreak);
        if (!FENCE.equals(firstLine.trim()) || firstLineBreak < 0) {
            return SkillFrontMatter.empty(text);
        }

        int cursor = firstLineBreak + lineBreakLength(text, firstLineBreak);
        int blockStart = cursor;
        int blockEnd = -1;
        int bodyStart = -1;
        while (cursor <= text.length()) {
            int lineBreak = indexOfLineBreak(text, cursor);
            int lineEnd = lineBreak < 0 ? text.length() : lineBreak;
            if (FENCE.equals(text.substring(cursor, lineEnd).trim())) {
                blockEnd = cursor;
                bodyStart = lineBreak < 0 ? text.length() : lineBreak + lineBreakLength(text, lineBreak);
                break;
            }
            if (lineBreak < 0) {
                break;
            }
            cursor = lineBreak + lineBreakLength(text, lineBreak);
        }
        if (blockEnd < 0) {
            // 栅栏未闭合：不猜测，整篇当正文。
            return SkillFrontMatter.empty(text);
        }

        String rawText = text.substring(blockStart, blockEnd).stripTrailing();
        String body = text.substring(bodyStart);
        FieldParseResult result = parseBlock(rawText);
        return new SkillFrontMatter(result.fields(), rawText, body, true, result.warnings());
    }

    /**
     * 只解析 frontmatter 文本块（不含 {@code ---} 栅栏），供 zip / 片段场景复用。
     * 降级信息不在此返回，调用方若需要请用 {@link #parse(String)}。
     */
    public static Map<String, Object> parseFields(String frontMatterBlock) {
        return parseBlock(frontMatterBlock).fields();
    }

    /**
     * 剥离一个前导 BOM。Windows 编辑器保存 UTF-8 时可能写入，
     * 不剥离会导致首行不是 {@code ---}，整个 frontmatter 被静默丢弃。
     */
    public static String stripBom(String text) {
        if (text != null && text.startsWith(BOM)) {
            return text.substring(1);
        }
        return text;
    }

    private record FieldParseResult(Map<String, Object> fields, List<String> warnings) {
    }

    private static FieldParseResult parseBlock(String block) {
        Map<String, Object> fields = new LinkedHashMap<>();
        if (block == null || block.isBlank()) {
            return new FieldParseResult(fields, List.of());
        }
        try {
            Object parsed = newYaml().load(block);
            if (parsed instanceof Map<?, ?> parsedMap) {
                for (Map.Entry<?, ?> entry : parsedMap.entrySet()) {
                    if (entry.getKey() == null) {
                        continue;
                    }
                    fields.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                return new FieldParseResult(fields, List.of());
            }
            return degraded(block, "yaml 顶层不是映射（实际为 "
                    + (parsed == null ? "null" : parsed.getClass().getSimpleName()) + "）");
        } catch (RuntimeException e) {
            return degraded(block, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static Yaml newYaml() {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(true);
        return new Yaml(new SafeConstructor(options));
    }

    /**
     * 降级解析：逐行按第一个 {@code :} 切分。此路径下会剥掉成对的外层引号，
     * 因为已经没有 YAML 语义可以依赖。正常路径<b>不会</b>修改任何值。
     */
    private static FieldParseResult degraded(String block, String reason) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (String line : block.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            int idx = line.indexOf(':');
            if (idx <= 0) {
                continue;
            }
            String key = line.substring(0, idx).trim();
            if (key.isEmpty()) {
                continue;
            }
            String value = line.substring(idx + 1).trim();
            if (value.length() >= 2
                    && ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'")))) {
                value = value.substring(1, value.length() - 1);
            }
            fields.put(key, value);
        }
        List<String> warnings = new ArrayList<>(1);
        warnings.add("frontmatter yaml 解析失败，已降级为逐行解析：" + reason);
        return new FieldParseResult(fields, warnings);
    }

    private static int indexOfLineBreak(String text, int from) {
        for (int i = Math.max(from, 0); i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r') {
                return i;
            }
        }
        return -1;
    }

    private static int lineBreakLength(String text, int at) {
        if (text.charAt(at) == '\r' && at + 1 < text.length() && text.charAt(at + 1) == '\n') {
            return 2;
        }
        return 1;
    }
}
