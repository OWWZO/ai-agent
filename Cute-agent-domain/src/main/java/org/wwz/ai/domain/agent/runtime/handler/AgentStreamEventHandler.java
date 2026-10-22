package org.wwz.ai.domain.agent.runtime.handler;


import org.wwz.ai.domain.agent.runtime.command.AgentExecutionCommand;
import org.wwz.ai.domain.agent.runtime.stream.AgentStreamAccumulator;
import org.wwz.ai.domain.agent.runtime.stream.AgentStreamEvent;
import org.wwz.ai.domain.agent.runtime.stream.AgentStreamResult;

import java.util.List;

/**
 * Agent 运行事件到增量响应的转换端口。
 * <p>
 * 实现类只负责协议适配，执行事实和历史回放数据仍由 Agent runtime 与 Execution Ledger 提供。
 */
public interface AgentStreamEventHandler {
    AgentStreamResult handle(AgentExecutionCommand request,
                             AgentStreamEvent response,
                             List<AgentStreamEvent> agentRespList,
                             AgentStreamAccumulator eventResult);
}
