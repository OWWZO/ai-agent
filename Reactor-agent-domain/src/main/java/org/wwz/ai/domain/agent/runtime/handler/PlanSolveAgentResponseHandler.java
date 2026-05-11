package org.wwz.ai.domain.agent.runtime.handler;


import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.reactor.model.multi.EventResult;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.reactor.model.response.AgentResponse;
import org.wwz.ai.domain.agent.reactor.model.response.GptProcessResult;
import org.wwz.ai.domain.agent.ledger.replay.ReplayProjector;

import java.util.List;

@Component
@Slf4j
public class PlanSolveAgentResponseHandler extends BaseAgentResponseHandler implements AgentResponseHandler {

    public PlanSolveAgentResponseHandler(ReplayProjector replayProjector) {
        super(replayProjector);
    }

    @Override
    public GptProcessResult handle(AgentRequest request, AgentResponse response, List<AgentResponse> agentRespList, EventResult eventResult) {
        try {
            return buildCanonicalIncrResult(request, eventResult, response);
        } catch (Exception e) {
            log.error("{} PlanSolveAgentResponseHandler handle error", request.getRequestId(), e);
            return null;
        }
    }
}
