package org.wwz.ai.domain.agent.runtime.exception;

/**
 * Token 数量超出限制异常
 */
public class TokenLimitExceeded extends RuntimeException {
    private final int currentTokens;
    private final int maxTokens;
    private final MessageType messageType;

    /**
     * 消息类型枚举
     */
    public enum MessageType {
        SYSTEM,
        USER,
        ASSISTANT,
        TOOL,
        UNKNOWN
    }

    /**
     * 构造函数
     */
    public TokenLimitExceeded(String message) {
        super(message);
        this.currentTokens = 0;
        this.maxTokens = 0;
        this.messageType = MessageType.UNKNOWN;
    }

    /**
     * 构造函数
     */
    public TokenLimitExceeded(int currentTokens, int maxTokens, MessageType messageType) {
        super(String.format(
                "Token limit exceeded: current=%d, max=%d, exceeded=%d, messageType=%s",
                currentTokens, maxTokens, currentTokens - maxTokens, messageType
        ));

        this.currentTokens = currentTokens;
        this.maxTokens = maxTokens;
        this.messageType = messageType;
    }

    /**
     * 获取当前 token 数量
     */
    public int getCurrentTokens() {
        return currentTokens;
    }

    /**
     * 获取最大允许 token 数量
     */
    public int getMaxTokens() {
        return maxTokens;
    }

    /**
     * 获取超出限制的 token 数量
     */
    public int getExceededTokens() {
        return currentTokens - maxTokens;
    }

    /**
     * 获取消息类型
     */
    public MessageType getMessageType() {
        return messageType;
    }
}