package org.wwz.ai.domain.agent.reactor.agent.enums;

/**
 * 会话对应的Agent类型 (持久化用)
 */
public enum ConversationAgentType {
    /** 聊天模式 */
    CHAT(0),
    /** 深度思考 */
    PLAN_SOLVE(1),
    /** 深度研究 */
    REACT(2);

    private final int code;

    ConversationAgentType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static ConversationAgentType fromCode(int code) {
        for (ConversationAgentType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid ConversationAgentType code: " + code);
    }

    /**
     * 根据前端参数推断会话Agent类型
     * @param outputStyle 产品形态
     * @param deepThink   深度思考标志 (null/0=REACT, 1=PLAN_SOLVE)
     */
    public static ConversationAgentType resolve(String outputStyle, Integer deepThink) {
        if ("chat".equalsIgnoreCase(outputStyle)) {
            return CHAT;
        }
        if (deepThink != null && deepThink == 1) {
            return PLAN_SOLVE;
        }
        return REACT;
    }
}
