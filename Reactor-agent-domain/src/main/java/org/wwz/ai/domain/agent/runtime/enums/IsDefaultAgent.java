package org.wwz.ai.domain.agent.runtime.enums;

/**
 * 智能体类型
 */
public enum IsDefaultAgent {
    IS_DEFAULT_AGENT(1),
    NOT_DEFAULT_AGENT(2),
    ;

    private final Integer value;

    IsDefaultAgent(Integer value) {
        this.value = value;
    }

    public Integer getValue() {
        return value;
    }
}