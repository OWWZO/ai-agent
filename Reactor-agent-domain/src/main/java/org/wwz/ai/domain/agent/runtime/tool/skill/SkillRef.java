package org.wwz.ai.domain.agent.runtime.tool.skill;

/**
 * Skill 限定引用。裸名称只在无冲突时可用。
 */
public record SkillRef(
        String id,
        String name,
        String source,
        String relativePath
) {

    public static final String SOURCE_BUILTIN = "builtin";

    public static SkillRef of(String source, String name, String relativePath) {
        String src = source == null || source.isBlank() ? SOURCE_BUILTIN : source.trim();
        String skillName = name == null ? "" : name.trim();
        String rel = relativePath == null || relativePath.isBlank()
                ? skillName
                : relativePath.trim().replace('\\', '/');
        return new SkillRef(src + ":" + rel, skillName, src, rel);
    }
}
