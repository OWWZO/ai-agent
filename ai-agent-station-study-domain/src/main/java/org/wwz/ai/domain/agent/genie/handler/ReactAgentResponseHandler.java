package org.wwz.ai.domain.agent.genie.handler;


import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.genie.model.multi.EventResult;
import org.wwz.ai.domain.agent.genie.model.req.AgentRequest;
import org.wwz.ai.domain.agent.genie.model.response.AgentResponse;
import org.wwz.ai.domain.agent.genie.model.response.GptProcessResult;

import java.util.List;

@Component
@Slf4j
public class ReactAgentResponseHandler  extends BaseAgentResponseHandler implements AgentResponseHandler {

    @Override
    public GptProcessResult handle(AgentRequest request, AgentResponse response, List<AgentResponse> agentRespList, EventResult eventResult) {
        try {
            return buildIncrResult(request, eventResult, response);
        } catch (Exception e) {
            log.error("{} ReactAgentResponseHandler handle error", request.getRequestId(), e);
            return null;
        }
    }
}
