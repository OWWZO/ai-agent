package org.wwz.ai.domain.agent.runtime.tool.skill;

/**
 * 按需读取 SKILL.md 与包内附属文件。
 */
public interface SkillLoader {

    SkillDocument load(SkillRef ref);

    SkillFile loadFile(SkillRef ref, String relativePath);
}
