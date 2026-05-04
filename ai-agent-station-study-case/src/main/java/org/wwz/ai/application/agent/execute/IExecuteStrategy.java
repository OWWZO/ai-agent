package org.wwz.ai.application.agent.execute;

import org.wwz.ai.application.agent.stream.AgentSessionStream;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;

/**
 * 应用层执行策略接口。
 * 由 case 层负责策略选择与流式输出适配，domain 只关注运行时内核。
 */
public interface IExecuteStrategy {

    void execute(AgentRequest request, AgentSessionStream stream) throws Exception;
}
