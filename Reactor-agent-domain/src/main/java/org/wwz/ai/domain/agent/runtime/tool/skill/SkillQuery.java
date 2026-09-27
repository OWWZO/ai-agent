package org.wwz.ai.domain.agent.runtime.tool.skill;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Skill 目录检索条件。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillQuery {

    public static final int DEFAULT_LIMIT = 10;
    public static final int MAX_LIMIT = 25;

    private String text;

    private String category;

    @Builder.Default
    private Set<String> tags = new LinkedHashSet<>();

    private String source;

    private Integer limit;

    @Builder.Default
    private Set<String> disabledNames = new LinkedHashSet<>();

    public int resolveLimit() {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
