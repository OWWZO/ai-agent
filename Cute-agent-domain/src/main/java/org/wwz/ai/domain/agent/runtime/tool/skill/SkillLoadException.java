package org.wwz.ai.domain.agent.runtime.tool.skill;

/**
 * Skill 加载异常
 */
public class SkillLoadException extends RuntimeException {

    public SkillLoadException(String message) {
        super(message);
    }

    public SkillLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
