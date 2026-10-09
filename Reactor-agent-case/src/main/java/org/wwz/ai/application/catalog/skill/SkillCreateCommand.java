package org.wwz.ai.application.catalog.skill;

/** 技能 Markdown 创建命令。 */
public record SkillCreateCommand(String name, String description, String content) {
}
