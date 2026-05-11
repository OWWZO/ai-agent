package org.wwz.ai.domain.agent.adapter.port;

/**
 * 远端流式会话句柄。
 * 用于在 domain 侧超时或降级时主动取消长连接。
 */
public interface RemoteStreamSession {

    /**
     * 主动取消流式请求。
     */
    void cancel();
}
