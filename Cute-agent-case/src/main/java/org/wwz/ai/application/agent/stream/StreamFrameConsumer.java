package org.wwz.ai.application.agent.stream;

/**
 * 回放时把投影缓冲帧交给单条观察连接。
 */
@FunctionalInterface
public interface StreamFrameConsumer {

    void accept(AgentSessionStreamFrame frame) throws Exception;
}
