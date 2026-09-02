package org.wwz.ai.domain.agent.runtime.llm;

import org.wwz.ai.domain.agent.ledger.model.AgentRunState;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;

/**
 * 跨异步边界携带 LLM 调用现场。
 * agentName/stepNo 存在 ThreadLocal 里，fallback 回调线程必须使用捕获值，不能重新读取。
 */
public final class LlmExecutionPosition {

    private final String agentName;
    private final Integer stepNo;

    private LlmExecutionPosition(String agentName, Integer stepNo) {
        this.agentName = agentName;
        this.stepNo = stepNo;
    }

    public static LlmExecutionPosition capture(AgentContext context) {
        return capture(context == null ? null : context.getAgentRunState());
    }

    public static LlmExecutionPosition capture(AgentRunState state) {
        if (state == null) {
            return new LlmExecutionPosition(null, null);
        }
        return new LlmExecutionPosition(state.getCurrentAgentName(), state.getCurrentStepNo());
    }

    public String agentName() {
        return agentName;
    }

    public Integer stepNo() {
        return stepNo;
    }

    public Scope restore(AgentContext context) {
        return restore(context == null ? null : context.getAgentRunState());
    }

    public Scope restore(AgentRunState state) {
        if (state == null) {
            return Scope.NOOP;
        }
        String previousAgentName = state.getCurrentAgentName();
        Integer previousStepNo = state.getCurrentStepNo();
        if (agentName == null && stepNo == null) {
            state.clearExecutionPosition();
        } else {
            state.markExecutionPosition(agentName, stepNo);
        }
        return new Scope(state, previousAgentName, previousStepNo);
    }

    public static final class Scope implements AutoCloseable {

        private static final Scope NOOP = new Scope(null, null, null);

        private final AgentRunState state;
        private final String previousAgentName;
        private final Integer previousStepNo;

        private Scope(AgentRunState state, String previousAgentName, Integer previousStepNo) {
            this.state = state;
            this.previousAgentName = previousAgentName;
            this.previousStepNo = previousStepNo;
        }

        @Override
        public void close() {
            if (state == null) {
                return;
            }
            if (previousAgentName == null && previousStepNo == null) {
                state.clearExecutionPosition();
            } else {
                state.markExecutionPosition(previousAgentName, previousStepNo);
            }
        }
    }
}
