package org.wwz.ai.domain.agent.runtime.printer;


import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.wwz.ai.domain.agent.runtime.enums.AgentType;
import org.wwz.ai.domain.agent.runtime.command.AgentExecutionCommand;

import java.util.Map;

/**
 * 面向日志的 Printer 实现，主要用于非流式执行、测试和本地诊断。
 */
@Slf4j
public class LogPrinter implements Printer {
    private final AgentExecutionCommand request;

    public LogPrinter(AgentExecutionCommand request) {
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
            log.debug("{} {} {} {} {} {} {}", request.getRequestId(), messageId, messageType, message, extraResultMap, digitalEmployee, isFinal);
            return;
        }
        log.info("{} {} {} {} {} {} {}", request.getRequestId(), messageId, messageType, message, extraResultMap, digitalEmployee, isFinal);
    }

    @Override
    public void close() {
    }

    @Override
    public void updateAgentType(AgentType agentType) {
    }
}
