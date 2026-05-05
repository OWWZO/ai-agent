package org.wwz.ai.domain.agent.runtime.printer;


import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.wwz.ai.domain.agent.runtime.enums.AgentType;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;

import java.util.Map;

@Slf4j
public class LogPrinter implements Printer {
    private final AgentRequest request;

    public LogPrinter(AgentRequest request) {
        this.request = request;
    }

    @Override
    public void send(String messageId, String messageType, Object message, String digitalEmployee, Boolean isFinal) {
        send(messageId, messageType, message, null, digitalEmployee, isFinal);
    }

    @Override
    public void send(String messageId,
                     String messageType,
                     Object message,
                     Map<String, Object> extraResultMap,
                     String digitalEmployee,
                     Boolean isFinal) {
        if ("deep_search".equals(messageType)) {
            message = JSON.toJSONString(message);
        }
        log.info("{} {} {} {} {} {} {}", request.getRequestId(), messageId, messageType, message, extraResultMap, digitalEmployee, isFinal);
    }

    @Override
    public void send(String messageType, Object message, String digitalEmployee) {
        send(null, messageType, message, digitalEmployee, true);
    }

    @Override
    public void send(String messageType, Object message) {
        send(null, messageType, message, null, true);
    }

    @Override
    public void send(String messageId, String messageType, Object message, Boolean isFinal) {
        send(messageId, messageType, message, (String) null, isFinal);
    }

    @Override
    public void sendWithResultMap(String messageId,
                                  String messageType,
                                  Object message,
                                  Map<String, Object> extraResultMap,
                                  Boolean isFinal) {
        send(messageId, messageType, message, extraResultMap, null, isFinal);
    }

    @Override
    public void sendWithResultMap(String messageType, Object message, Map<String, Object> extraResultMap) {
        send(null, messageType, message, extraResultMap, null, true);
    }

    @Override
    public void close() {
    }

    @Override
    public void updateAgentType(AgentType agentType) {
    }
}
