package org.wwz.ai.domain.agent.genie.agent.printer;

import com.alibaba.fastjson.JSON;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.wwz.ai.domain.agent.genie.agent.enums.AgentType;
import org.wwz.ai.domain.agent.genie.agent.util.StringUtil;
import org.wwz.ai.domain.agent.genie.model.req.AgentRequest;
import org.wwz.ai.domain.agent.genie.model.response.AgentResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Setter
public class SSEPrinter implements Printer {
    private SseEmitter emitter;
    private AgentRequest request;
    private Integer agentType;

    public SSEPrinter(SseEmitter emitter, AgentRequest request, Integer agentType) {
        this.emitter = emitter;
        this.request = request;
        this.agentType = agentType;
    }

    @Override
    public void send(String messageId, String messageType, Object message, String digitalEmployee, Boolean isFinal) {
        try {
            if (Objects.isNull(messageId)) {
                messageId = StringUtil.getUUID();
            }
            log.info("{} sse send {} {} {}", request.getRequestId(), messageType, message, digitalEmployee);
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
            if (!StringUtils.isEmpty(digitalEmployee)) {
                response.setDigitalEmployee(digitalEmployee);
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
                        log.error("ssePrinter task_summary format is illegal");
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

            emitter.send(response);

        } catch (Exception e) {
            log.error("sse send error ", e);
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
        send(messageId, messageType, message, null, isFinal);
    }

    @Override
    public void close() {
        emitter.complete();
    }

    @Override
    public void updateAgentType(AgentType agentType) {
        this.agentType = agentType.getValue();
    }
}