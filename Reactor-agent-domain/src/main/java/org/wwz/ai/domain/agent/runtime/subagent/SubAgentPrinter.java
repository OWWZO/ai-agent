package org.wwz.ai.domain.agent.runtime.subagent;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.enums.AgentType;
import org.wwz.ai.domain.agent.runtime.printer.Printer;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 子 Agent 输出包装（对标 cc-haha agent_progress 挂到父 Agent tool_use 下）。
 * 所有 SSE 事件自动打上 parentToolUseId / subAgentId，供前端嵌套展示。
 */
public class SubAgentPrinter implements Printer {

    public static final String KEY_PARENT_TOOL_USE_ID = "parentToolUseId";
    public static final String KEY_SUB_AGENT_ID = "subAgentId";
    public static final String KEY_SUB_AGENT_TYPE = "subAgentType";
    public static final String KEY_SUB_AGENT_DESCRIPTION = "subAgentDescription";

    private final Printer delegate;
    private final String parentToolUseId;
    private final String subAgentId;
    private final String subAgentType;
    private final String subAgentDescription;

    public SubAgentPrinter(Printer delegate,
                           String parentToolUseId,
                           String subAgentId,
                           String subAgentType,
                           String subAgentDescription) {
        this.delegate = delegate;
        this.parentToolUseId = parentToolUseId;
        this.subAgentId = subAgentId;
        this.subAgentType = subAgentType;
        this.subAgentDescription = subAgentDescription;
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
        if (delegate == null) {
            return;
        }
        // 子 Agent 思考过程默认不刷主时间线，只保留工具活动（对齐 cc-haha 折叠）
        if ("tool_thought".equals(messageType) || "llm_reasoning".equals(messageType)) {
            return;
        }
        Object enrichedMessage = enrichMessage(message);
        Map<String, Object> enrichedExtra = enrichExtra(extraResultMap);
        delegate.send(messageId, messageType, enrichedMessage, enrichedExtra, digitalEmployee, isFinal);
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
        // 不关闭父 printer
    }

    @Override
    public void updateAgentType(AgentType agentType) {
        if (delegate != null) {
            delegate.updateAgentType(agentType);
        }
    }

    @SuppressWarnings("unchecked")
    private Object enrichMessage(Object message) {
        if (!(message instanceof Map)) {
            return message;
        }
        Map<String, Object> map = new LinkedHashMap<>((Map<String, Object>) message);
        putTags(map);
        return map;
    }

    private Map<String, Object> enrichExtra(Map<String, Object> extraResultMap) {
        Map<String, Object> map = extraResultMap == null
                ? new HashMap<>()
                : new LinkedHashMap<>(extraResultMap);
        putTags(map);
        return map;
    }

    private void putTags(Map<String, Object> map) {
        if (StringUtils.isNotBlank(parentToolUseId)) {
            map.put(KEY_PARENT_TOOL_USE_ID, parentToolUseId);
        }
        if (StringUtils.isNotBlank(subAgentId)) {
            map.put(KEY_SUB_AGENT_ID, subAgentId);
        }
        if (StringUtils.isNotBlank(subAgentType)) {
            map.put(KEY_SUB_AGENT_TYPE, subAgentType);
        }
        if (StringUtils.isNotBlank(subAgentDescription)) {
            map.put(KEY_SUB_AGENT_DESCRIPTION, subAgentDescription);
        }
    }
}
