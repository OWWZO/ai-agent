package org.wwz.ai.test.domain.sessionmemory;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessageEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话事件 payload 测试样本构造器。
 */
public final class SessionEventPayloadFixtureBuilder {

    private SessionEventPayloadFixtureBuilder() {
    }

    public static AgentMessageEvent toolThoughtEvent(Long messageId,
                                                     int seqNo,
                                                     String toolUseId,
                                                     String toolName,
                                                     Object toolArguments,
                                                     String contentText,
                                                     String taskId,
                                                     Integer taskOrder) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messageType", "tool_thought");
        payload.put("toolUseId", toolUseId);
        payload.put("toolName", toolName);
        payload.put("toolArguments", toolArguments);
        payload.put("taskId", taskId);
        payload.put("taskOrder", taskOrder);
        payload.put("resultMap", Map.of(
                "messageType", "tool_thought",
                "toolName", toolName
        ));
        return AgentMessageEvent.builder()
                .messageId(messageId)
                .seqNo(seqNo)
                .eventType("tool_thought")
                .taskId(taskId)
                .taskOrder(taskOrder)
                .contentText(contentText)
                .payloadJson(JSON.toJSONString(payload))
                .status("completed")
                .build();
    }

    public static AgentMessageEvent toolResultEvent(Long messageId,
                                                    int seqNo,
                                                    String eventType,
                                                    String eventSubType,
                                                    String toolUseId,
                                                    String toolName,
                                                    Object toolArguments,
                                                    String contentText,
                                                    String taskId,
                                                    Integer taskOrder,
                                                    List<JSONObject> artifactRefs) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messageType", eventType);
        payload.put("toolUseId", toolUseId);
        payload.put("toolName", toolName);
        payload.put("toolArguments", toolArguments);
        payload.put("taskId", taskId);
        payload.put("taskOrder", taskOrder);
        if (artifactRefs != null && !artifactRefs.isEmpty()) {
            payload.put("artifactRefs", artifactRefs);
        }
        Map<String, Object> resultMap = new LinkedHashMap<>();
        resultMap.put("messageType", eventSubType == null ? eventType : eventSubType);
        resultMap.put("answer", contentText);
        payload.put("resultMap", resultMap);
        return AgentMessageEvent.builder()
                .messageId(messageId)
                .seqNo(seqNo)
                .eventType(eventType)
                .eventSubType(eventSubType)
                .displayArea("report".equalsIgnoreCase(eventSubType) ? "workspace" : "timeline")
                .taskId(taskId)
                .taskOrder(taskOrder)
                .contentText(contentText)
                .payloadJson(JSON.toJSONString(payload))
                .status("completed")
                .build();
    }

    public static JSONObject artifactRef(String displayName, String url) {
        JSONObject artifactRef = new JSONObject(new LinkedHashMap<>());
        artifactRef.put("displayName", displayName);
        artifactRef.put("resourceKey", displayName);
        artifactRef.put("downloadUrl", url);
        artifactRef.put("previewUrl", url);
        artifactRef.put("missing", false);
        return artifactRef;
    }
}
