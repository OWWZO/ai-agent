package org.wwz.ai.domain.agent.runtime.tool.skill;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 将会话级短索引写入 system prompt，不把 SKILL.md 正文放进 tools[]。
 * <p>
 * 三级降级策略（参照 hermes-agent {@code agent/prompt_builder.py} 的描述截断 + 分类压缩思路）：
 * <ol>
 *   <li>完整索引（描述 {@value #MAX_DESCRIPTION_CHARS} 字符）；</li>
 *   <li>描述压缩到 {@value #COMPACT_DESCRIPTION_CHARS} 字符；</li>
 *   <li>放不下的分类整体压成一行「N 个 skill（用 skills_search 查询）」，
 *       保证 skill 名称的可见性不丢。</li>
 * </ol>
 */
public final class SkillPromptIndexBuilder {

    public static final int MAX_DESCRIPTION_CHARS = 80;
    public static final int SEARCH_DESCRIPTION_CHARS = 300;
    public static final int MAX_INDEX_SKILLS = 200;

    /** 第二档降级时单条描述的最大字符数。 */
    public static final int COMPACT_DESCRIPTION_CHARS = 40;

    /** 默认的索引总字符预算；&lt;= 0 表示不限制。 */
    public static final int DEFAULT_PROMPT_INDEX_MAX_CHARS = 6000;

    public static final String AVAILABLE_SKILLS_OPEN = "<available_skills>";
    public static final String AVAILABLE_SKILLS_CLOSE = "</available_skills>";
    public static final String DEGRADED_HINT = "（索引已按预算精简，可用 skills_search 检索完整列表）";

    private SkillPromptIndexBuilder() {
    }

    public static String build(Collection<SkillDescriptor> descriptors) {
        return build(descriptors, Set.of());
    }

    public static String build(Collection<SkillDescriptor> descriptors, Set<String> disabledNames) {
        return build(descriptors, disabledNames, DEFAULT_PROMPT_INDEX_MAX_CHARS, MAX_DESCRIPTION_CHARS);
    }

    /**
     * @param maxChars         索引总字符预算，&lt;= 0 表示不限制
     * @param descriptionChars 单条描述的最大字符数，&lt;= 0 时回落到 {@link #MAX_DESCRIPTION_CHARS}
     */
    public static String build(Collection<SkillDescriptor> descriptors,
                               Set<String> disabledNames,
                               int maxChars,
                               int descriptionChars) {
        int effectiveDescriptionChars = descriptionChars > 0 ? descriptionChars : MAX_DESCRIPTION_CHARS;
        Map<String, List<SkillDescriptor>> byCategory = groupByCategory(accept(descriptors, disabledNames));

        String full = render(byCategory, effectiveDescriptionChars, false);
        if (maxChars <= 0 || full.length() <= maxChars) {
            return full;
        }

        String compact = render(byCategory, Math.min(COMPACT_DESCRIPTION_CHARS, effectiveDescriptionChars), true);
        if (compact.length() <= maxChars) {
            return compact;
        }

        return renderWithCategoryBudget(byCategory, maxChars);
    }

    public static String truncate(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        String trimmed = text.strip();
        if (maxChars <= 0 || trimmed.length() <= maxChars) {
            return trimmed;
        }
        if (maxChars == 1) {
            return "…";
        }
        return trimmed.substring(0, maxChars - 1) + "…";
    }

    private static List<SkillDescriptor> accept(Collection<SkillDescriptor> descriptors, Set<String> disabledNames) {
        List<SkillDescriptor> accepted = new ArrayList<>();
        if (descriptors == null) {
            return accepted;
        }
        List<SkillDescriptor> sorted = new ArrayList<>(descriptors);
        sorted.sort(Comparator.comparing(SkillDescriptor::getName, String.CASE_INSENSITIVE_ORDER));
        Set<String> disabled = disabledNames == null ? Set.of() : disabledNames;
        for (SkillDescriptor descriptor : sorted) {
            if (descriptor == null || descriptor.getName() == null || descriptor.getName().isBlank()) {
                continue;
            }
            if (disabled.contains(descriptor.getName())) {
                continue;
            }
            if (accepted.size() >= MAX_INDEX_SKILLS) {
                break;
            }
            accepted.add(descriptor);
        }
        return accepted;
    }

    private static Map<String, List<SkillDescriptor>> groupByCategory(List<SkillDescriptor> descriptors) {
        Map<String, List<SkillDescriptor>> byCategory = new TreeMap<>();
        for (SkillDescriptor descriptor : descriptors) {
            String category = descriptor.getCategory() == null || descriptor.getCategory().isBlank()
                    ? SkillDescriptor.CATEGORY_GENERAL
                    : descriptor.getCategory();
            byCategory.computeIfAbsent(category, key -> new ArrayList<>()).add(descriptor);
        }
        return byCategory;
    }

    private static Map<String, SkillDescriptor> dedupe(List<SkillDescriptor> descriptors) {
        Map<String, SkillDescriptor> unique = new LinkedHashMap<>();
        for (SkillDescriptor descriptor : descriptors) {
            unique.putIfAbsent(descriptor.getName(), descriptor);
        }
        return unique;
    }

    private static StringBuilder header(boolean degraded) {
        StringBuilder header = new StringBuilder();
        header.append("# Skills\n");
        header.append("如果当前任务与某个 skill 相关，必须先调用 skill_view 加载它。\n");
        header.append("如果无法判断 skill 名称，先调用 skills_search。\n");
        header.append("不要直接使用 workspace_read 搜索 skill 主文档。\n\n");
        if (degraded) {
            header.append(DEGRADED_HINT).append('\n');
        }
        header.append(AVAILABLE_SKILLS_OPEN).append('\n');
        return header;
    }

    private static String render(Map<String, List<SkillDescriptor>> byCategory,
                                 int descriptionChars,
                                 boolean degraded) {
        StringBuilder index = header(degraded);
        if (byCategory.isEmpty()) {
            index.append("  （当前没有可用 skill）\n");
        } else {
            for (Map.Entry<String, List<SkillDescriptor>> entry : byCategory.entrySet()) {
                index.append("  ").append(entry.getKey()).append(":\n");
                for (SkillDescriptor descriptor : dedupe(entry.getValue()).values()) {
                    index.append("    - ")
                            .append(descriptor.getName())
                            .append(": ")
                            .append(truncate(descriptor.getDescription(), descriptionChars))
                            .append('\n');
                }
            }
        }
        index.append(AVAILABLE_SKILLS_CLOSE).append('\n');
        return index.toString();
    }

    /**
     * 第三档：分类整体降级为一行，保留分类名与条目数，避免静默丢弃 skill。
     */
    private static String renderWithCategoryBudget(Map<String, List<SkillDescriptor>> byCategory, int maxChars) {
        StringBuilder index = header(true);
        int used = index.length() + AVAILABLE_SKILLS_CLOSE.length() + 1;

        for (Map.Entry<String, List<SkillDescriptor>> entry : byCategory.entrySet()) {
            Map<String, SkillDescriptor> unique = dedupe(entry.getValue());
            StringBuilder full = new StringBuilder();
            full.append("  ").append(entry.getKey()).append(":\n");
            for (SkillDescriptor descriptor : unique.values()) {
                full.append("    - ")
                        .append(descriptor.getName())
                        .append(": ")
                        .append(truncate(descriptor.getDescription(), COMPACT_DESCRIPTION_CHARS))
                        .append('\n');
            }
            if (used + full.length() <= maxChars) {
                index.append(full);
                used += full.length();
                continue;
            }
            String compactLine = "  " + entry.getKey() + ": " + unique.size()
                    + " 个 skill（用 skills_search 查询）\n";
            if (used + compactLine.length() > maxChars) {
                compactLine = "  " + entry.getKey() + ": " + unique.size() + " 个\n";
            }
            index.append(compactLine);
            used += compactLine.length();
        }

        index.append(AVAILABLE_SKILLS_CLOSE).append('\n');
        return index.toString();
    }
}
