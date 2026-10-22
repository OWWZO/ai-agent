package org.wwz.ai.domain.agent.runtime.tool.skill;

/**
 * 搜索命中。score 仅用于排序，不进入 system prompt。
 */
public record SkillSearchHit(SkillDescriptor descriptor, double score) {
}
