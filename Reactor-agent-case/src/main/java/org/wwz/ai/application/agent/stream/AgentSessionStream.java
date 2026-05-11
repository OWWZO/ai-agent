package org.wwz.ai.application.agent.stream;

import org.wwz.ai.domain.agent.adapter.port.AgentMessageStream;

/**
 * 应用层会话输出端口。
 * 触发层可以用 SSE、WebSocket 等协议适配，实现端到端的输出复用。
 */
public interface AgentSessionStream extends AgentMessageStream {
}
