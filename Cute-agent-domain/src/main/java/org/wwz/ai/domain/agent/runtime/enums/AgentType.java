package org.wwz.ai.domain.agent.runtime.enums;

/**
 * 智能体类型
 */
public enum AgentType {
    PLAN_SOLVE(3),
    REACT(5);

    private final Integer value;

    AgentType(Integer value) {
        this.value = value;
    }

    public Integer getValue() {
        return value;
    }

    public static AgentType fromCode(int value) {
        for (AgentType type : AgentType.values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid AgentType code: " + value);
    }
}
