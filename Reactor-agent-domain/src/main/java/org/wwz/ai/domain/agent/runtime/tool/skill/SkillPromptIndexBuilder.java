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
 */
public final class SkillPromptIndexBuilder {

    public static final int MAX_DESCRIPTION_CHARS = 80;
    public static final int SEARCH_DESCRIPTION_CHARS = 300;
    public static final int MAX_INDEX_SKILLS = 200;
    public static final String AVAILABLE_SKILLS_OPEN = "<available_skills>";
    public static final String AVAILABLE_SKILLS_CLOSE = "</available_skills>";

    private SkillPromptIndexBuilder() {
    }

    public static String build(Collection<SkillDescriptor> descriptors) {
        return build(descriptors, Set.of());
    }

    public static String build(Collection<SkillDescriptor> descriptors, Set<String> disabledNames) {
        Map<String, List<SkillDescriptor>> byCategory = new TreeMap<>();
        int accepted = 0;
        if (descriptors != null) {
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
                if (accepted >= MAX_INDEX_SKILLS) {
                    break;
                }
                String category = descriptor.getCategory() == null || descriptor.getCategory().isBlank()
                        ? SkillDescriptor.CATEGORY_GENERAL
                        : descriptor.getCategory();
                byCategory.computeIfAbsent(category, key -> new ArrayList<>()).add(descriptor);
                accepted++;
            }
        }
        StringBuilder index = new StringBuilder();
        index.append("# Skills\n");
        index.append("如果当前任务与某个 skill 相关，必须先调用 skill_view 加载它。\n");
        index.append("如果无法判断 skill 名称，先调用 skills_search。\n");
        index.append("不要直接使用 workspace_read 搜索 skill 主文档。\n\n");
        index.append(AVAILABLE_SKILLS_OPEN).append('\n');
        if (byCategory.isEmpty()) {
            index.append("  （当前没有可用 skill）\n");
        } else {
            for (Map.Entry<String, List<SkillDescriptor>> entry : byCategory.entrySet()) {
                index.append("  ").append(entry.getKey()).append(":\n");
                Map<String, SkillDescriptor> unique = new LinkedHashMap<>();
                for (SkillDescriptor descriptor : entry.getValue()) {
                    unique.putIfAbsent(descriptor.getName(), descriptor);
                }
                for (SkillDescriptor descriptor : unique.values()) {
                    index.append("    - ")
                            .append(descriptor.getName())
                            .append(": ")
                            .append(truncate(descriptor.getDescription(), MAX_DESCRIPTION_CHARS))
                            .append('\n');
                }
            }
        }
        index.append(AVAILABLE_SKILLS_CLOSE).append('\n');
        return index.toString();
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
}
