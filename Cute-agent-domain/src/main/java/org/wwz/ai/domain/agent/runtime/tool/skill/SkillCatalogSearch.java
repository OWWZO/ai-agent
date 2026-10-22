package org.wwz.ai.domain.agent.runtime.tool.skill;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * name/description/tags 关键词匹配。第一阶段不用向量。
 */
final class SkillCatalogSearch {

    private SkillCatalogSearch() {
    }

    static List<SkillSearchHit> search(Collection<SkillDescriptor> descriptors, SkillQuery query) {
        SkillQuery safeQuery = query == null ? new SkillQuery() : query;
        String text = safeQuery.getText() == null ? "" : safeQuery.getText().trim();
        String category = safeQuery.getCategory() == null ? "" : safeQuery.getCategory().trim();
        String source = safeQuery.getSource() == null ? "" : safeQuery.getSource().trim();
        Set<String> tags = safeQuery.getTags() == null ? Set.of() : safeQuery.getTags();
        Set<String> disabled = safeQuery.getDisabledNames() == null ? Set.of() : safeQuery.getDisabledNames();
        List<String> tokens = tokenize(text);

        List<SkillSearchHit> hits = new ArrayList<>();
        for (SkillDescriptor descriptor : descriptors) {
            if (descriptor == null || descriptor.getName() == null) {
                continue;
            }
            if (disabled.contains(descriptor.getName())) {
                continue;
            }
            if (!category.isEmpty() && !category.equalsIgnoreCase(nullToEmpty(descriptor.getCategory()))) {
                continue;
            }
            if (!source.isEmpty() && !source.equalsIgnoreCase(nullToEmpty(descriptor.getSource()))) {
                continue;
            }
            if (!tags.isEmpty()) {
                Set<String> skillTags = descriptor.getTags() == null ? Set.of() : descriptor.getTags();
                boolean matchedTag = false;
                for (String tag : tags) {
                    if (tag != null && skillTags.contains(tag)) {
                        matchedTag = true;
                        break;
                    }
                }
                if (!matchedTag) {
                    continue;
                }
            }
            double score = score(descriptor, text, tokens);
            if (text.isEmpty() || score > 0) {
                hits.add(new SkillSearchHit(descriptor, text.isEmpty() ? 0.1 : score));
            }
        }
        hits.sort(Comparator
                .comparingDouble(SkillSearchHit::score).reversed()
                .thenComparing(hit -> hit.descriptor().getName(), String.CASE_INSENSITIVE_ORDER));
        int limit = safeQuery.resolveLimit();
        if (hits.size() > limit) {
            return new ArrayList<>(hits.subList(0, limit));
        }
        return hits;
    }

    private static double score(SkillDescriptor descriptor, String text, List<String> tokens) {
        String name = nullToEmpty(descriptor.getName());
        String description = nullToEmpty(descriptor.getDescription());
        String nameLower = name.toLowerCase(Locale.ROOT);
        String descLower = description.toLowerCase(Locale.ROOT);
        String queryLower = text.toLowerCase(Locale.ROOT);
        if (!text.isEmpty() && name.equalsIgnoreCase(text)) {
            return 1.0;
        }
        if (!queryLower.isEmpty() && nameLower.equals(queryLower)) {
            return 0.98;
        }
        if (!queryLower.isEmpty() && nameLower.contains(queryLower)) {
            return 0.9;
        }
        Set<String> tags = descriptor.getTags() == null ? Set.of() : descriptor.getTags();
        for (String tag : tags) {
            if (tag != null && tag.equalsIgnoreCase(text)) {
                return 0.85;
            }
        }
        if (tokens.isEmpty()) {
            return 0;
        }
        int matched = 0;
        for (String token : tokens) {
            boolean inName = nameLower.contains(token);
            boolean inDesc = descLower.contains(token);
            boolean inTag = false;
            for (String tag : tags) {
                if (tag != null && tag.toLowerCase(Locale.ROOT).contains(token)) {
                    inTag = true;
                    break;
                }
            }
            if (inName || inDesc || inTag) {
                matched++;
            }
        }
        if (matched == tokens.size()) {
            return 0.7;
        }
        if (matched > 0) {
            return 0.35 + (0.25 * matched / tokens.size());
        }
        return 0;
    }

    private static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String[] parts = text.toLowerCase(Locale.ROOT).split("[^\\p{IsAlphabetic}\\p{IsDigit}]+");
        List<String> tokens = new ArrayList<>();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                tokens.add(part.trim());
            }
        }
        return tokens;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
