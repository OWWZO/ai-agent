package org.wwz.ai.domain.agent.runtime.handler;


import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.runtime.command.AgentExecutionCommand;
import org.wwz.ai.domain.agent.runtime.stream.AgentStreamAccumulator;
import org.wwz.ai.domain.agent.runtime.stream.AgentStreamEvent;
import org.wwz.ai.domain.agent.runtime.stream.AgentStreamResult;
import org.wwz.ai.domain.agent.ledger.replay.ReplayProjector;

import java.util.List;

/**
 * Agent 的增量响应处理器。
 * <p>
 * React 与 PlanSolve 共用同一份 canonical event 投影（实时事件与历史回放同源），
 * 因此只有一个实现；两者差异只体现在日志标签，由 {@link #handle} 内的 agentType 区分。
 */
@Component
@Slf4j
public class CanonicalAgentStreamEventHandler extends BaseAgentStreamEventHandler implements AgentStreamEventHandler {

    public CanonicalAgentStreamEventHandler(ReplayProjector replayProjector) {
        super(replayProjector);
    }

    @Override
    public AgentStreamResult handle(AgentExecutionCommand request, AgentStreamEvent response, List<AgentStreamEvent> agentRespList, AgentStreamAccumulator eventResult) {
        try {
            // 不在处理器内重新拼装工具结果，避免实时链路与账本回放出现字段差异。
            return buildCanonicalIncrResult(request, eventResult, response);
        } catch (Exception e) {
            log.error("{} AgentStreamEventHandler handle error", request.getRequestId(), e);
            return null;
        }
    }
}
