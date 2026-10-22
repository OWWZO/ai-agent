package org.wwz.ai.domain.agent.runtime.tool.skill;

/**
 * Skill 包内附属文件清单项。
 */
public record SkillFileDescriptor(
        String path,
        String kind,
        String description,
        long size
) {
}
