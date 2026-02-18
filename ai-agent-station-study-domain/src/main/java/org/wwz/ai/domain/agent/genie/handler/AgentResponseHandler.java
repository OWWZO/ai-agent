package org.wwz.ai.domain.agent.genie.handler;


import org.wwz.ai.domain.agent.genie.model.multi.EventResult;
import org.wwz.ai.domain.agent.genie.model.req.AgentRequest;
import org.wwz.ai.domain.agent.genie.model.response.AgentResponse;
import org.wwz.ai.domain.agent.genie.model.response.GptProcessResult;

import java.util.List;

public interface AgentResponseHandler {
    GptProcessResult handle(AgentRequest request,
                            AgentResponse response,
                            List<AgentResponse> agentRespList,
                            EventResult eventResult);
}
