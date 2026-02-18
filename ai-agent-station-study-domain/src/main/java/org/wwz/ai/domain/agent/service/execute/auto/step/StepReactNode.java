package org.wwz.ai.domain.agent.service.execute.auto.step;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.model.entity.AgentExecuteResultEntity;
import org.wwz.ai.domain.agent.model.entity.ExecuteCommandEntity;
import org.wwz.ai.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;
import org.wwz.ai.domain.agent.genie.agent.agent.AgentContext;
import org.wwz.ai.domain.agent.genie.agent.agent.ReactImplAgent;
import org.wwz.ai.domain.agent.genie.agent.printer.Printer;
import org.wwz.ai.domain.agent.genie.agent.tool.ToolCollection;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
public class StepReactNode extends AbstractExecuteSupport {

    @Override
    protected String doApply(ExecuteCommandEntity requestParameter, DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        String sessionId = requestParameter.getSessionId() != null ? requestParameter.getSessionId() : UUID.randomUUID().toString();
        int step = 1;
        String stepName = "ReAct";

        sendSseResult(dynamicContext, AgentExecuteResultEntity.createStreamStart(step, stepName, "react_stream", sessionId));

        Printer printer = new Printer() {
            @Override
            public void send(String messageId, String messageType, Object message, String digitalEmployee, Boolean isFinal) {
                String content = message == null ? "" : String.valueOf(message);
                if ("result".equals(messageType)) {
                    sendSseResult(dynamicContext, AgentExecuteResultEntity.createSummaryResult(content, sessionId));
                } else {
                    sendSseResult(dynamicContext, AgentExecuteResultEntity.createStreamDelta(step, stepName, messageType, content, sessionId));
                }
            }

            @Override
            public void send(String messageType, Object message) {
                send(null, messageType, message, null, true);
            }

            @Override
            public void send(String messageType, Object message, String digitalEmployee) {
                send(null, messageType, message, digitalEmployee, true);
            }

            @Override
            public void send(String messageId, String messageType, Object message, Boolean isFinal) {
                send(messageId, messageType, message, null, isFinal);
            }

            @Override
            public void close() {
                sendSseResult(dynamicContext, AgentExecuteResultEntity.createStreamEnd(step, stepName, "react_stream", sessionId));
            }

            @Override
            public void updateAgentType(org.wwz.ai.domain.agent.genie.agent.enums.AgentType agentType) {
            }
        };

        ToolCollection toolCollection = new ToolCollection();

        AgentContext agentContext = AgentContext.builder()
                .requestId(sessionId)
                .sessionId(sessionId)
                .query(requestParameter.getMessage())
                .printer(printer)
                .toolCollection(toolCollection)
                .dateInfo(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .isStream(true)
                .agentType(5)
                .templateType("empty")
                .build();
        toolCollection.setAgentContext(agentContext);

        ReactImplAgent react = new ReactImplAgent(agentContext);
        String result = react.run(requestParameter.getMessage());

        if (result != null && !result.isEmpty()) {
            sendSseResult(dynamicContext, AgentExecuteResultEntity.createStreamDelta(step, stepName, "react", result, sessionId));
        }
        sendSseResult(dynamicContext, AgentExecuteResultEntity.createStreamEnd(step, stepName, "react_stream", sessionId));
        dynamicContext.setCompleted(true);
        return "react_done";
    }

    @Override
    public StrategyHandler<ExecuteCommandEntity, DefaultAutoAgentExecuteStrategyFactory.DynamicContext, String> get(ExecuteCommandEntity requestParameter, DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return defaultStrategyHandler;
    }
}

