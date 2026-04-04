package org.wwz.ai.domain.agent.reactor.agent.enums;

/**
 * 消息状态
 */
public enum MessageStatus {
    /** 流式传输中 */
    STREAMING(0),
    /** 已完成 */
    COMPLETED(1),
    /** 错误 */
    ERROR(2),
    /** 强制停止 */
    FORCE_STOPPED(3);

    private final int code;

    MessageStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
