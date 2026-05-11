package org.wwz.ai.domain.agent.runtime.handler;


import org.wwz.ai.domain.agent.reactor.model.multi.EventResult;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.reactor.model.response.AgentResponse;
import org.wwz.ai.domain.agent.reactor.model.response.GptProcessResult;

import java.util.List;

public interface AgentResponseHandler {
    GptProcessResult handle(AgentRequest request,
                            AgentResponse response,
                            List<AgentResponse> agentRespList,
                            EventResult eventResult);
}
