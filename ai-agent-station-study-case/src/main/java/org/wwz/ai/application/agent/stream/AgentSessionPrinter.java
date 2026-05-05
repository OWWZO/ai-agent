package org.wwz.ai.application.agent.stream;

import com.alibaba.fastjson.JSON;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.wwz.ai.domain.agent.runtime.enums.AgentType;
import org.wwz.ai.domain.agent.runtime.printer.Printer;
import org.wwz.ai.domain.agent.runtime.util.StringUtil;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.reactor.model.response.AgentResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 基于应用层输出端口的 Printer 适配器。
 * 统一复用既有 AgentResponse 协议，避免领域层直接依赖 SSE 实现。
 */
@Slf4j
@Setter
public class AgentSessionPrinter implements Printer {

    private final AgentSessionStream stream;
    private final AgentRequest request;
    private Integer agentType;

    public AgentSessionPrinter(AgentSessionStream stream, AgentRequest request, Integer agentType) {
        this.stream = stream;
        this.request = request;
        this.agentType = agentType;
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
        try {
            if (Objects.isNull(messageId)) {
                messageId = StringUtil.getUUID();
            }

            log.info("{} stream send {} {} {}", request.getRequestId(), messageType, message, digitalEmployee);

            boolean finish = "result".equals(messageType);
            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("agentType", agentType);

            AgentResponse response = AgentResponse.builder()
                    .requestId(request.getRequestId())
                    .messageId(messageId)
                    .messageType(messageType)
                    .messageTime(String.valueOf(System.currentTimeMillis()))
                    .resultMap(resultMap)
                    .finish(finish)
                    .isFinal(isFinal)
                    .build();

            if (extraResultMap != null && !extraResultMap.isEmpty()) {
                resultMap.putAll(extraResultMap);
            }

            if (!StringUtils.isEmpty(digitalEmployee)) {
                response.setDigitalEmployee(digitalEmployee);
                resultMap.put("digitalEmployee", digitalEmployee);
            }

            switch (messageType) {
                case "tool_thought":
                    response.setToolThought((String) message);
                    break;
                case "task":
                    response.setTask(((String) message).replaceAll("^执行顺序(\\d+)\\.\\s?", ""));
                    break;
                case "task_summary":
                    if (message instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> taskSummary = (Map<String, Object>) message;
                        Object summary = taskSummary.get("taskSummary");
                        response.setResultMap(taskSummary);
                        response.setTaskSummary(summary != null ? summary.toString() : null);
                    } else {
                        log.error("agentSessionPrinter task_summary format is illegal");
                    }
                    break;
                case "plan_thought":
                    response.setPlanThought((String) message);
                    break;
                case "plan":
                    AgentResponse.Plan plan = new AgentResponse.Plan();
                    BeanUtils.copyProperties(message, plan);
                    response.setPlan(AgentResponse.formatSteps(plan));
                    break;
                case "tool_result":
                    response.setToolResult((AgentResponse.ToolResult) message);
                    break;
                case "tool_call":
                case "browser":
                case "code":
                case "html":
                case "markdown":
                case "ppt":
                case "file":
                case "knowledge":
                case "deep_search":
                case "data_analysis":
                    response.setResultMap(JSON.parseObject(JSON.toJSONString(message)));
                    response.getResultMap().put("agentType", agentType);
                    break;
                case "agent_stream":
                    response.setResult((String) message);
                    break;
                case "result":
                    if (message instanceof String) {
                        response.setResult((String) message);
                    } else if (message instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> taskResult = (Map<String, Object>) message;
                        Object summary = taskResult.get("taskSummary");
                        response.setResultMap(taskResult);
                        response.setResult(summary != null ? summary.toString() : null);
                    } else {
                        Map<String, Object> taskResult = JSON.parseObject(JSON.toJSONString(message));
                        response.setResultMap(taskResult);
                        response.setResult(taskResult.get("taskSummary").toString());
                    }
                    response.getResultMap().put("agentType", agentType);
                    break;
                default:
                    break;
            }

            stream.send(response);
        } catch (Exception e) {
            log.error("stream send error", e);
        }
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
        stream.complete();
    }

    @Override
    public void updateAgentType(AgentType agentType) {
        this.agentType = agentType.getValue();
    }
}
