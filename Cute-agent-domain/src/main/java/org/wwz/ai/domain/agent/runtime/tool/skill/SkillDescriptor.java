package org.wwz.ai.domain.agent.runtime.tool.skill;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Skill 元数据。启动扫描只保留这一层，不持有 SKILL.md 正文。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillDescriptor {

    public static final String CATEGORY_GENERAL = "general";

    private SkillRef ref;

    private String name;

    private String description;

    @Builder.Default
    private String category = CATEGORY_GENERAL;

    @Builder.Default
    private Set<String> tags = new LinkedHashSet<>();

    @Builder.Default
    private String source = SkillRef.SOURCE_BUILTIN;

    private String version;

    private Path basePath;

    @Builder.Default
    private List<SkillFileDescriptor> files = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> frontMatter = new LinkedHashMap<>();
}
