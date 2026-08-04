package org.wwz.ai.domain.agent.runtime.handler;


import org.wwz.ai.domain.agent.reactor.model.multi.EventResult;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.reactor.model.response.AgentResponse;
import org.wwz.ai.domain.agent.reactor.model.response.GptProcessResult;

import java.util.List;

/**
 * Agent 运行事件到增量响应的转换端口。
 * <p>
 * 实现类只负责协议适配，执行事实和历史回放数据仍由 Agent runtime 与 Execution Ledger 提供。
 */
public interface AgentResponseHandler {
    GptProcessResult handle(AgentRequest request,
                            AgentResponse response,
                            List<AgentResponse> agentRespList,
                            EventResult eventResult);
}
