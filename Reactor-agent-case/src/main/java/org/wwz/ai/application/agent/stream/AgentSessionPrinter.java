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

    /**
     * 刷新后续绑浏览器观察流。主聊天路径 stream 为 {@link AgentResponseProjectionStream}，
     * 续绑其下游 SSE；返回应写回 ActiveAgentRunRegistry 的根流。
     *
     * @return 根观察流；无法续绑时返回 null
     */
    public AgentSessionStream attachObserver(AgentSessionStream observer) {
        return attachObserver(observer, 0L);
    }

    public AgentSessionStream attachObserver(AgentSessionStream observer, long lastEventSeq) {
        if (observer == null || stream == null) {
            return null;
        }
        if (stream instanceof AgentResponseProjectionStream projection) {
            projection.rebindDownstream(observer, lastEventSeq);
            return stream;
        }
        log.warn("{} printer stream is not rebindable projection, follow skipped",
                request == null ? "-" : request.getRequestId());
        return null;
    }

    public AgentSessionStream getStream() {
        return stream;
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
            // Printer 是领域事件到 AgentResponse/SSE 的协议适配边界：先建立公共元数据，
            // 再按 messageType 填充专属字段，最后一次性发送，避免领域层依赖传输细节。
            if (Objects.isNull(messageId)) {
                messageId = StringUtil.getUUID();
            }

            if ("tool_call_delta".equals(messageType)) {
                // [tool-stream-diag] 确认 delta 已越过 domain printer → case SSE 边界
                String argsHint = "";
                if (message instanceof Map<?, ?> map) {
                    Object raw = map.get("argumentsRaw");
                    if (raw == null) {
                        raw = map.get("argumentsText");
                    }
                    if (raw != null) {
                        argsHint = " argsLen=" + String.valueOf(raw).length();
                    }
                    Object tn = map.get("toolName");
                    Object tcid = map.get("toolCallId");
                    log.info("{} [tool-stream-diag] AgentSessionPrinter tool_call_delta messageId={} toolName={} toolCallId={}{}",
                            request.getRequestId(), messageId, tn, tcid, argsHint);
                } else {
                    log.info("{} [tool-stream-diag] AgentSessionPrinter tool_call_delta messageId={} msgClass={}",
                            request.getRequestId(), messageId, message == null ? null : message.getClass().getName());
                }
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
                case "llm_reasoning":
                    // 原生 CoT：有 tool_call / 无 tool_call 均推
                    response.setReasoningContent(message == null ? null : String.valueOf(message));
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
                case "tool_call_delta":
                case "ask_user_question":
                case "plan_approval":
                case "plan_mode_entered":
                case "session_tasks":
                case "user_brief":
                case "user_inject":
                case "subagent_progress":
                case "llm_retry":
                case "context_usage":
                case "browser":
                case "code":
                case "html":
                case "markdown":
                case "ppt":
                case "file":
                case "knowledge":
                case "deep_search":
                case "data_analysis":
                case "ui_tree":
                case "ui_patch":
                    // 结构化事件统一转为 resultMap；extraResultMap 用于补充父子 Agent
                    // 关联信息，必须在序列化后再合并，防止被 message 内容覆盖。
                    response.setResultMap(JSON.parseObject(JSON.toJSONString(message)));
                    response.getResultMap().put("agentType", agentType);
                    response.getResultMap().put("messageType", messageType);
                    // 子 Agent 嵌套标签（parentToolUseId 等）经 extraResultMap 传入，
                    // 上面 setResultMap 会覆盖，这里再合并一次。
                    if (extraResultMap != null && !extraResultMap.isEmpty()) {
                        response.getResultMap().putAll(extraResultMap);
                    }
                    break;
                case "agent_stream":
                    response.setResult((String) message);
                    break;
                case "result":
                    // result 是本轮终态事件：兼容字符串、Map 和普通对象三种旧调用形态，
                    // 同时把 taskSummary 提升到 response.result 供历史/前端直接读取。
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
