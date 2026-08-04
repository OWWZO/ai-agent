package org.wwz.ai.domain.agent.runtime.handler;


import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.reactor.model.multi.EventResult;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.reactor.model.response.AgentResponse;
import org.wwz.ai.domain.agent.reactor.model.response.GptProcessResult;
import org.wwz.ai.domain.agent.ledger.replay.ReplayProjector;

import java.util.List;

/**
 * PlanSolve Agent 的增量响应处理器。
 * <p>
 * PlanSolve 已收敛为单主代理执行，因此响应协议与 React 共用 canonical event 投影。
 */
@Component
@Slf4j
public class PlanSolveAgentResponseHandler extends BaseAgentResponseHandler implements AgentResponseHandler {

    public PlanSolveAgentResponseHandler(ReplayProjector replayProjector) {
        super(replayProjector);
    }

    @Override
    public GptProcessResult handle(AgentRequest request, AgentResponse response, List<AgentResponse> agentRespList, EventResult eventResult) {
        try {
            // 不在处理器内重新拼装工具结果，避免实时链路与账本回放出现字段差异。
            return buildCanonicalIncrResult(request, eventResult, response);
        } catch (Exception e) {
            log.error("{} PlanSolveAgentResponseHandler handle error", request.getRequestId(), e);
            return null;
        }
    }
}
