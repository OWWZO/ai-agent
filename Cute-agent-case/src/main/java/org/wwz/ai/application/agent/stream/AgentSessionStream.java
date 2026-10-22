package org.wwz.ai.application.agent.stream;

import org.wwz.ai.domain.agent.adapter.port.AgentMessageStream;
import org.wwz.ai.domain.agent.runtime.stream.AgentStreamEvent;

/**
 * 应用层会话输出端口。
 * 触发层可以用 SSE、WebSocket 等协议适配，实现端到端的输出复用。
 */
public interface AgentSessionStream extends AgentMessageStream {

    /**
     * Case 内部接收 Domain runtime event 的入口。Trigger 实现只会收到 frame。
     */
    default void sendRuntimeEvent(AgentStreamEvent event) throws Exception {
        send(event);
    }

    /**
     * Case 对外发布 frame 的显式入口。
     */
    default void sendFrame(AgentSessionStreamFrame frame) throws Exception {
        send(frame);
    }
}
