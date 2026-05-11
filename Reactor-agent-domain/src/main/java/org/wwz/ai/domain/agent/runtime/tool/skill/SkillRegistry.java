package org.wwz.ai.domain.agent.runtime.tool.skill;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;

/**
 * Skill 注册中心
 */
public interface SkillRegistry {

    void refresh();

    boolean isEnabled();

    Collection<SkillDefinition> listSkills();

    Optional<SkillDefinition> findSkill(String skillName);

    SkillDefinition getRequiredSkill(String skillName);

    SkillScriptDefinition getRequiredScript(String skillName, String scriptName);

    Path assertPathAllowed(Path candidatePath);

    String buildSkillDescription();
}
