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
 * React Agent 的增量响应处理器。
 * <p>
 * React 和 PlanSolve 共用 canonical event 投影，当前类只保留入口类型和日志语义差异。
 */
@Component
@Slf4j
public class ReactAgentResponseHandler extends BaseAgentResponseHandler implements AgentResponseHandler {

    public ReactAgentResponseHandler(ReplayProjector replayProjector) {
        super(replayProjector);
    }

    @Override
    public GptProcessResult handle(AgentRequest request, AgentResponse response, List<AgentResponse> agentRespList, EventResult eventResult) {
        try {
            // 统一走基础处理器，确保实时事件与历史回放使用同一份 projector 结果。
            return buildCanonicalIncrResult(request, eventResult, response);
        } catch (Exception e) {
            log.error("{} ReactAgentResponseHandler handle error", request.getRequestId(), e);
            return null;
        }
    }
}
