package org.wwz.ai.domain.agent.reactor.service.support;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessageEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/**
 * 统一读取事件事实账本，并把标准列投影成实时链路可复用的数据结构。
 */
public class ConversationEventFactSupport {

    private static final Set<String> SEMANTIC_EVENT_TYPES = Set.of(
            "assistant_thought",
            "plan_snapshot",
            "tool_use",
            "tool_result",
            "artifact_reference");

    public boolean isSemanticFactEvent(AgentMessageEvent event) {
        if (event == null || !StringUtils.hasText(event.getEventType())) {
            return false;
        }
        return SEMANTIC_EVENT_TYPES.contains(lower(event.getEventType()));
    }

    public ConversationEventFact readFact(AgentMessageEvent event) {
        JSONObject extensionPayload = parseObject(event == null ? null : event.getPayloadJson());
        JSONObject structuredData = parseObject(event == null ? null : event.getStructuredDataJson());
        List<JSONObject> artifactRefs = parseArrayObjects(event == null ? null : event.getArtifactRefsJson());
        String historyMessageId = firstNonBlank(
                extensionPayload.getString("messageId"),
                synthesizeHistoryMessageId(event));
        return new ConversationEventFact(
                event == null ? null : event.getMessageId(),
                event == null ? null : event.getSeqNo(),
                event == null ? null : lower(event.getEventType()),
                event == null ? null : event.getEventSubType(),
                event == null ? null : event.getDisplayArea(),
                event == null ? null : event.getTaskId(),
                event == null ? null : event.getTaskOrder(),
                event == null ? null : event.getToolUseId(),
                event == null ? null : event.getToolName(),
                normalizeJsonString(event == null ? null : event.getToolArgumentsJson()),
                Boolean.TRUE.equals(event == null ? null : event.getReferenceOnly()),
                artifactRefs,
                structuredData,
                extensionPayload,
                historyMessageId,
                event == null ? null : event.getTitle(),
                event == null ? null : event.getContentText());
    }

    public ProjectedHistoryEvent projectHistoryEvent(AgentMessageEvent event) {
        if (!isSemanticFactEvent(event)) {
            return null;
        }
        ConversationEventFact fact = readFact(event);
        return switch (fact.eventType()) {
            case "assistant_thought" -> projectAssistantThought(fact);
            case "plan_snapshot" -> projectPlanSnapshot(fact);
            case "tool_result" -> projectToolResult(fact);
            case "artifact_reference" -> projectArtifactReference(fact);
            case "tool_use" -> null;
            default -> null;
        };
    }

    private ProjectedHistoryEvent projectAssistantThought(ConversationEventFact fact) {
        if ("tool".equalsIgnoreCase(fact.eventSubType())) {
            JSONObject resultMap = new JSONObject(new LinkedHashMap<>());
            resultMap.put("messageType", "tool_thought");
            resultMap.put("toolThought", fact.contentText());
            resultMap.put("toolName", fact.toolName());
            resultMap.put("toolArguments", parseRawJson(fact.toolArgumentsJson()));
            resultMap.put("isFinal", true);
            return new ProjectedHistoryEvent(
                    "task",
                    "tool_thought",
                    defaultDisplayArea(fact.displayArea(), "timeline"),
                    fact.taskId(),
                    fact.taskOrder(),
                    fact.historyMessageId(),
                    buildTaskPayload(fact, resultMap));
        }

        JSONObject resultMap = new JSONObject(new LinkedHashMap<>());
        resultMap.put("planThought", fact.contentText());
        resultMap.put("isFinal", true);
        JSONObject payload = new JSONObject(new LinkedHashMap<>());
        payload.put("messageType", "plan_thought");
        payload.put("messageId", fact.historyMessageId());
        payload.put("resultMap", resultMap);
        return new ProjectedHistoryEvent(
                "plan_thought",
                "final_state",
                defaultDisplayArea(fact.displayArea(), "timeline"),
                null,
                null,
                fact.historyMessageId(),
                payload);
    }

    private ProjectedHistoryEvent projectPlanSnapshot(ConversationEventFact fact) {
        JSONObject resultData = cloneJson(fact.structuredData());
        if ("task".equalsIgnoreCase(fact.eventSubType())) {
            return new ProjectedHistoryEvent(
                    "task",
                    "final_state",
                    defaultDisplayArea(fact.displayArea(), "timeline"),
                    fact.taskId(),
                    fact.taskOrder(),
                    fact.historyMessageId(),
                    buildTaskPayload(fact, ensureMessageType(resultData, "task")));
        }

        JSONObject payload = new JSONObject(new LinkedHashMap<>());
        payload.put("messageType", "plan");
        payload.put("messageId", fact.historyMessageId());
        payload.put("resultMap", resultData);
        return new ProjectedHistoryEvent(
                "plan",
                "final_state",
                defaultDisplayArea(fact.displayArea(), "timeline"),
                null,
                null,
                fact.historyMessageId(),
                payload);
    }

    private ProjectedHistoryEvent projectToolResult(ConversationEventFact fact) {
        ToolResultProjection projection = resolveToolResultProjection(fact.eventSubType(), fact.toolName());
        JSONObject resultData = cloneJson(fact.structuredData());
        if (resultData.isEmpty()) {
            resultData.put("messageType", projection.messageType());
            if (StringUtils.hasText(fact.contentText())) {
                resultData.put("answer", fact.contentText());
            }
            resultData.put("isFinal", true);
        }
        ensureResultMetadata(resultData, fact, projection.messageType());
        return new ProjectedHistoryEvent(
                projection.messageType(),
                projection.subType(),
                defaultDisplayArea(fact.displayArea(), "timeline"),
                fact.taskId(),
                fact.taskOrder(),
                fact.historyMessageId(),
                buildTaskPayload(fact, resultData));
    }

    private ProjectedHistoryEvent projectArtifactReference(ConversationEventFact fact) {
        JSONObject resultMap = new JSONObject(new LinkedHashMap<>());
        resultMap.put("messageType", "file");
        resultMap.put("isFinal", true);
        return new ProjectedHistoryEvent(
                "file",
                firstNonBlank(fact.eventSubType(), "generated_file"),
                defaultDisplayArea(fact.displayArea(), "workspace"),
                fact.taskId(),
                fact.taskOrder(),
                fact.historyMessageId(),
                buildTaskPayload(fact, resultMap));
    }

    private JSONObject buildTaskPayload(ConversationEventFact fact, JSONObject resultMap) {
        JSONObject payload = new JSONObject(new LinkedHashMap<>());
        payload.put("messageType", "task");
        payload.put("messageId", fact.historyMessageId());
        payload.put("taskId", fact.taskId());
        payload.put("taskOrder", fact.taskOrder());
        payload.put("resultMap", resultMap);
        if (!CollectionUtils.isEmpty(fact.artifactRefs())) {
            JSONArray artifactRefs = new JSONArray();
            artifactRefs.addAll(fact.artifactRefs());
            payload.put("artifactRefs", artifactRefs);
        }
        return payload;
    }

    private void ensureResultMetadata(JSONObject resultData,
                                      ConversationEventFact fact,
                                      String messageType) {
        ensureMessageType(resultData, messageType);
        resultData.putIfAbsent("isFinal", true);
        if (!StringUtils.hasText(resultData.getString("messageTime")) && fact.seqNo() != null) {
            resultData.put("messageTime", String.valueOf(fact.seqNo()));
        }
        if (!StringUtils.hasText(resultData.getString("messageId"))) {
            resultData.put("messageId", fact.historyMessageId());
        }
    }

    private JSONObject ensureMessageType(JSONObject resultData, String messageType) {
        if (resultData == null) {
            resultData = new JSONObject(new LinkedHashMap<>());
        }
        if (!StringUtils.hasText(resultData.getString("messageType")) && StringUtils.hasText(messageType)) {
            resultData.put("messageType", messageType);
        }
        return resultData;
    }

    private ToolResultProjection resolveToolResultProjection(String canonicalSubType, String fallbackToolName) {
        if (!StringUtils.hasText(canonicalSubType)) {
            return new ToolResultProjection(firstNonBlank(fallbackToolName, "task"), null);
        }
        int splitIndex = canonicalSubType.indexOf('.');
        if (splitIndex < 0) {
            return new ToolResultProjection(canonicalSubType, null);
        }
        return new ToolResultProjection(
                canonicalSubType.substring(0, splitIndex),
                canonicalSubType.substring(splitIndex + 1));
    }

    private String synthesizeHistoryMessageId(AgentMessageEvent event) {
        if (event == null || event.getMessageId() == null) {
            return null;
        }
        return "history:" + event.getMessageId() + ":" + (event.getSeqNo() == null ? 0 : event.getSeqNo());
    }

    public JSONObject parseObject(String json) {
        if (!StringUtils.hasText(json)) {
            return new JSONObject(new LinkedHashMap<>());
        }
        try {
            Object parsed = JSON.parse(json);
            if (parsed instanceof JSONObject jsonObject) {
                return jsonObject;
            }
            if (parsed instanceof java.util.Map<?, ?> mapValue) {
                return new JSONObject(new LinkedHashMap<>((java.util.Map<String, Object>) mapValue));
            }
        } catch (Exception ignored) {
        }
        return new JSONObject(new LinkedHashMap<>());
    }

    public List<JSONObject> parseArrayObjects(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            JSONArray jsonArray = JSON.parseArray(json);
            List<JSONObject> objects = new ArrayList<>(jsonArray.size());
            for (Object item : jsonArray) {
                if (item instanceof JSONObject jsonObject) {
                    objects.add(jsonObject);
                }
            }
            return objects;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    public String normalizeJsonString(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            Object parsed = JSON.parse(json);
            if (parsed instanceof JSONObject jsonObject && jsonObject.isEmpty()) {
                return null;
            }
            if (parsed instanceof JSONArray jsonArray && jsonArray.isEmpty()) {
                return null;
            }
            return JSON.toJSONString(parsed);
        } catch (Exception ignored) {
            return json;
        }
    }

    private Object parseRawJson(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return JSON.parse(json);
        } catch (Exception ignored) {
            return json;
        }
    }

    private JSONObject cloneJson(JSONObject jsonObject) {
        if (jsonObject == null || jsonObject.isEmpty()) {
            return new JSONObject(new LinkedHashMap<>());
        }
        return JSON.parseObject(jsonObject.toJSONString());
    }

    private String defaultDisplayArea(String displayArea, String fallback) {
        return StringUtils.hasText(displayArea) ? displayArea : fallback;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase();
    }

    public record ConversationEventFact(Long messageId,
                                        Integer seqNo,
                                        String eventType,
                                        String eventSubType,
                                        String displayArea,
                                        String taskId,
                                        Integer taskOrder,
                                        String toolUseId,
                                        String toolName,
                                        String toolArgumentsJson,
                                        boolean referenceOnly,
                                        List<JSONObject> artifactRefs,
                                        JSONObject structuredData,
                                        JSONObject extensionPayload,
                                        String historyMessageId,
                                        String title,
                                        String contentText) {
    }

    public record ProjectedHistoryEvent(String eventType,
                                        String eventSubType,
                                        String displayArea,
                                        String taskId,
                                        Integer taskOrder,
                                        String messageId,
                                        JSONObject payload) {
    }

    private record ToolResultProjection(String messageType, String subType) {
    }
}
