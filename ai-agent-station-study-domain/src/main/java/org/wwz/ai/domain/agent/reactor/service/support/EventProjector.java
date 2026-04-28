package org.wwz.ai.domain.agent.reactor.service.support;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.wwz.ai.domain.agent.reactor.model.multi.OrderedEvent;
import org.wwz.ai.domain.agent.reactor.model.response.AgentResponse;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 负责把标准 AgentResponse 投影为事件账本。
 */
@Component
public class EventProjector {

    @Resource
    private SessionArtifactRestoreSupport sessionArtifactRestoreSupport;

    public List<OrderedEvent> project(AgentResponse agentResponse,
                                      Map<String, Object> eventDataMap,
                                      AtomicInteger seqCounter) {
        String rawMessageType = agentResponse == null ? null : agentResponse.getMessageType();
        if (!shouldPersistFinalDetail(agentResponse, rawMessageType)) {
            return Collections.emptyList();
        }

        Map<String, Object> safeEventDataMap = eventDataMap == null
                ? Collections.emptyMap()
                : new LinkedHashMap<>(eventDataMap);
        JSONObject normalizedPayload = ConversationEventPayloadNormalizer.normalizePayload(safeEventDataMap);
        String taskId = valueToString(safeEventDataMap.get("taskId"));
        Integer taskOrder = convertToInteger(safeEventDataMap.get("taskOrder"));
        String messageId = firstNonBlank(
                valueToString(safeEventDataMap.get("messageId")),
                agentResponse == null ? null : agentResponse.getMessageId());
        ToolCallDescriptor toolDescriptor = resolveToolCallDescriptor(agentResponse, safeEventDataMap, messageId, taskId, taskOrder);
        List<OrderedEvent> projectedEvents = new ArrayList<>();

        if ("plan_thought".equals(rawMessageType)) {
            projectedEvents.add(buildAssistantThoughtFactEvent(
                    seqCounter.getAndIncrement(),
                    "plan",
                    taskId,
                    taskOrder,
                    messageId,
                    resolveEventTitle(agentResponse),
                    extractContentText(agentResponse),
                    null));
            return projectedEvents;
        }

        String payloadMessageType = valueToString(safeEventDataMap.get("messageType"));
        if ("plan".equals(payloadMessageType)) {
            projectedEvents.add(buildPlanSnapshotFactEvent(
                    seqCounter.getAndIncrement(),
                    "plan",
                    null,
                    null,
                    messageId,
                    resolvePlanDisplayTitle(agentResponse == null ? null : agentResponse.getPlan()),
                    extractPlanContentText(agentResponse == null ? null : agentResponse.getPlan()),
                    parseAgentResponseJson(agentResponse == null ? null : agentResponse.getPlan())));
            return projectedEvents;
        }

        if ("task".equals(rawMessageType)) {
            projectedEvents.add(buildPlanSnapshotFactEvent(
                    seqCounter.getAndIncrement(),
                    "task",
                    taskId,
                    taskOrder,
                    messageId,
                    resolveEventTitle(agentResponse),
                    extractContentText(agentResponse),
                    parseAgentResponseJson(agentResponse)));
            return projectedEvents;
        }

        if ("tool_thought".equals(rawMessageType)) {
            projectedEvents.add(buildAssistantThoughtFactEvent(
                    seqCounter.getAndIncrement(),
                    "tool",
                    taskId,
                    taskOrder,
                    messageId,
                    resolveEventTitle(agentResponse),
                    extractContentText(agentResponse),
                    toolDescriptor));
            if (toolDescriptor.hasToolCall()) {
                projectedEvents.add(buildToolUseFactEvent(
                        seqCounter.getAndIncrement(),
                        taskId,
                        taskOrder,
                        messageId,
                        rawMessageType,
                        toolDescriptor));
            }
            return projectedEvents;
        }

        String artifactRefsJson = buildArtifactRefsJson(agentResponse, normalizedPayload);
        if (shouldPersistArtifactReferenceFact(rawMessageType, artifactRefsJson)) {
            projectedEvents.add(buildArtifactReferenceFactEvent(
                    seqCounter.getAndIncrement(),
                    taskId,
                    taskOrder,
                    messageId,
                    resolveEventTitle(agentResponse),
                    extractContentText(agentResponse),
                    artifactRefsJson,
                    buildExtensionPayload(normalizedPayload)));
            return projectedEvents;
        }

        if (!shouldPersistToolResultFact(rawMessageType, normalizedPayload)) {
            return Collections.emptyList();
        }

        String canonicalToolResultSubType = resolveCanonicalToolResultSubType(rawMessageType, agentResponse, normalizedPayload);
        projectedEvents.add(buildToolResultFactEvent(
                seqCounter.getAndIncrement(),
                rawMessageType,
                canonicalToolResultSubType,
                resolveFinalDisplayArea(rawMessageType, canonicalToolResultSubType),
                taskId,
                taskOrder,
                messageId,
                toolDescriptor,
                resolveEventTitle(agentResponse),
                extractContentText(agentResponse),
                artifactRefsJson,
                buildStructuredDataJson(parseAgentResponseJson(agentResponse)),
                buildExtensionPayload(normalizedPayload),
                ConversationEventPayloadNormalizer.isReferenceOnly(
                        normalizedPayload,
                        rawMessageType,
                        canonicalToolResultSubType,
                        extractContentText(agentResponse))));
        return projectedEvents;
    }

    private OrderedEvent buildAssistantThoughtFactEvent(int seqNo,
                                                        String thoughtType,
                                                        String taskId,
                                                        Integer taskOrder,
                                                        String messageId,
                                                        String title,
                                                        String contentText,
                                                        ToolCallDescriptor toolDescriptor) {
        return buildProjectedEvent(
                "assistant_thought|" + thoughtType + "|" + defaultString(taskId) + "|" + defaultString(messageId),
                seqNo,
                "assistant_thought",
                thoughtType,
                "timeline",
                taskId,
                taskOrder,
                messageId,
                title,
                contentText,
                toolDescriptor,
                false,
                null,
                null,
                null);
    }

    private OrderedEvent buildPlanSnapshotFactEvent(int seqNo,
                                                    String snapshotType,
                                                    String taskId,
                                                    Integer taskOrder,
                                                    String messageId,
                                                    String title,
                                                    String contentText,
                                                    JSONObject resultData) {
        return buildProjectedEvent(
                "plan_snapshot|" + snapshotType + "|" + defaultString(taskId) + "|" + defaultString(messageId),
                seqNo,
                "plan_snapshot",
                snapshotType,
                "timeline",
                taskId,
                taskOrder,
                messageId,
                title,
                contentText,
                null,
                false,
                null,
                buildStructuredDataJson(resultData),
                null);
    }

    private OrderedEvent buildToolUseFactEvent(int seqNo,
                                               String taskId,
                                               Integer taskOrder,
                                               String messageId,
                                               String rawMessageType,
                                               ToolCallDescriptor toolDescriptor) {
        return buildProjectedEvent(
                "tool_use|" + defaultString(toolDescriptor.toolUseId()),
                seqNo,
                "tool_use",
                resolveCanonicalToolUseSubType(toolDescriptor, rawMessageType),
                "timeline",
                taskId,
                taskOrder,
                messageId,
                "准备调用 " + defaultString(toolDescriptor.toolName()),
                buildToolUseContent(toolDescriptor),
                toolDescriptor,
                false,
                null,
                null,
                null);
    }

    private OrderedEvent buildToolResultFactEvent(int seqNo,
                                                  String sourceType,
                                                  String canonicalSubType,
                                                  String displayArea,
                                                  String taskId,
                                                  Integer taskOrder,
                                                  String messageId,
                                                  ToolCallDescriptor toolDescriptor,
                                                  String title,
                                                  String contentText,
                                                  String artifactRefsJson,
                                                  String structuredDataJson,
                                                  String payloadJson,
                                                  boolean referenceOnly) {
        return buildProjectedEvent(
                resolveToolResultFactKey(sourceType, canonicalSubType, taskId, messageId, toolDescriptor.toolUseId()),
                seqNo,
                "tool_result",
                canonicalSubType,
                displayArea,
                taskId,
                taskOrder,
                messageId,
                title,
                contentText,
                toolDescriptor,
                referenceOnly,
                artifactRefsJson,
                structuredDataJson,
                payloadJson);
    }

    private OrderedEvent buildArtifactReferenceFactEvent(int seqNo,
                                                         String taskId,
                                                         Integer taskOrder,
                                                         String messageId,
                                                         String title,
                                                         String contentText,
                                                         String artifactRefsJson,
                                                         String payloadJson) {
        String resolvedContentText = StringUtils.hasText(contentText) ? contentText : "已生成或更新产物";
        return buildProjectedEvent(
                "artifact_reference|" + defaultString(taskId) + "|" + defaultString(messageId),
                seqNo,
                "artifact_reference",
                "generated_file",
                "workspace",
                taskId,
                taskOrder,
                messageId,
                StringUtils.hasText(title) ? title : "生成文件",
                resolvedContentText,
                null,
                true,
                artifactRefsJson,
                null,
                payloadJson);
    }

    private ToolCallDescriptor resolveToolCallDescriptor(AgentResponse agentResponse,
                                                         Map<String, Object> eventDataMap,
                                                         String messageId,
                                                         String taskId,
                                                         Integer taskOrder) {
        String fallbackToolUseId = StringUtils.hasText(messageId)
                ? messageId
                : defaultString(taskId) + ":" + defaultString(taskOrder == null ? null : String.valueOf(taskOrder));
        String toolUseId = firstNonBlank(
                valueToString(eventDataMap.get("toolUseId")),
                valueToString(eventDataMap.get("toolCallId")),
                fallbackToolUseId);
        String toolName = firstNonBlank(
                valueToString(eventDataMap.get("toolName")),
                agentResponse != null && agentResponse.getToolResult() != null ? agentResponse.getToolResult().getToolName() : null,
                agentResponse == null ? null : agentResponse.getMessageType());
        String toolArgumentsJson = stringifyToolArguments(
                firstNonNull(
                        eventDataMap.get("toolArguments"),
                        eventDataMap.get("toolParam"),
                        agentResponse != null && agentResponse.getToolResult() != null
                                ? agentResponse.getToolResult().getToolParam()
                                : null));
        return new ToolCallDescriptor(toolUseId, toolName, toolArgumentsJson);
    }

    private String buildToolUseContent(ToolCallDescriptor descriptor) {
        if (StringUtils.hasText(descriptor.toolArgumentsJson())) {
            return "准备调用 " + defaultString(descriptor.toolName()) + "，参数：" + descriptor.toolArgumentsJson();
        }
        return "准备调用 " + defaultString(descriptor.toolName());
    }

    private String resolveCanonicalToolUseSubType(ToolCallDescriptor descriptor,
                                                  String rawMessageType) {
        String toolName = StringUtils.hasText(descriptor.toolName()) ? descriptor.toolName() : rawMessageType;
        if (!StringUtils.hasText(toolName)) {
            return "tool";
        }
        return switch (toolName) {
            case "multimodalagent_tool" -> "knowledge";
            case "report_tool", "file_tool" -> "file_generation";
            default -> toolName.endsWith("_tool") ? toolName.substring(0, toolName.length() - 5) : toolName;
        };
    }

    private String resolveCanonicalToolResultSubType(String rawMessageType,
                                                     AgentResponse agentResponse,
                                                     JSONObject normalizedPayload) {
        return switch (rawMessageType) {
            case "deep_search" -> "deep_search." + defaultString(firstNonBlank(
                    resolveEventSubType(agentResponse),
                    extractMessageType(normalizedPayload),
                    "report"));
            case "browser" -> "browser.result";
            case "knowledge" -> "knowledge.answer";
            case "markdown" -> "markdown.report";
            case "html" -> "html.page";
            case "ppt" -> "ppt.deck";
            case "code" -> "code.bundle";
            case "file" -> "file.output";
            case "data_analysis" -> "data_analysis.output";
            default -> rawMessageType + "." + defaultString(firstNonBlank(
                    extractMessageType(normalizedPayload),
                    "result"));
        };
    }

    private boolean shouldPersistArtifactReferenceFact(String rawMessageType, String artifactRefsJson) {
        return "result".equals(rawMessageType) && StringUtils.hasText(artifactRefsJson);
    }

    private boolean shouldPersistToolResultFact(String rawMessageType, JSONObject normalizedPayload) {
        return !"agent_stream".equals(rawMessageType) && !"result".equals(rawMessageType);
    }

    private String buildArtifactRefsJson(AgentResponse agentResponse, JSONObject normalizedPayload) {
        List<JSONObject> artifactRefs = new ArrayList<>(ConversationEventPayloadNormalizer.extractNormalizedArtifactRefs(normalizedPayload));
        artifactRefs.addAll(extractArtifactRefsFromResultMap(agentResponse == null ? null : agentResponse.getResultMap()));
        List<JSONObject> deduplicatedRefs = sessionArtifactRestoreSupport.deduplicateArtifactRefs(artifactRefs);
        return deduplicatedRefs.isEmpty() ? null : JSON.toJSONString(deduplicatedRefs);
    }

    @SuppressWarnings("unchecked")
    private List<JSONObject> extractArtifactRefsFromResultMap(Map<String, Object> resultMap) {
        if (resultMap == null || resultMap.isEmpty()) {
            return List.of();
        }

        List<JSONObject> artifactRefs = new ArrayList<>();
        Object directArtifactRefs = resultMap.get("artifactRefs");
        if (directArtifactRefs instanceof List<?> directArtifactRefList && !directArtifactRefList.isEmpty()) {
            artifactRefs.addAll(ConversationEventPayloadNormalizer.extractNormalizedArtifactRefs(
                    ConversationEventPayloadNormalizer.normalizePayload(Map.of("artifactRefs", directArtifactRefList))));
        }

        Object fileInfo = firstNonNull(resultMap.get("fileInfo"), resultMap.get("fileList"));
        if (fileInfo instanceof List<?> fileInfoList && !fileInfoList.isEmpty()) {
            for (Object item : fileInfoList) {
                if (!(item instanceof Map<?, ?> itemMap)) {
                    continue;
                }
                JSONObject artifactRef = new JSONObject(new LinkedHashMap<>());
                artifactRef.put("artifactType", firstNonBlank(valueToString(itemMap.get("artifactType")), valueToString(itemMap.get("fileType"))));
                artifactRef.put("displayName", firstNonBlank(valueToString(itemMap.get("displayName")), valueToString(itemMap.get("fileName")), valueToString(itemMap.get("name"))));
                artifactRef.put("resourceKey", firstNonBlank(
                        valueToString(itemMap.get("resourceKey")),
                        valueToString(itemMap.get("downloadUrl")),
                        valueToString(itemMap.get("ossUrl")),
                        valueToString(itemMap.get("domainUrl"))));
                artifactRef.put("downloadUrl", firstNonBlank(valueToString(itemMap.get("downloadUrl")), valueToString(itemMap.get("ossUrl"))));
                artifactRef.put("previewUrl", firstNonBlank(valueToString(itemMap.get("previewUrl")), valueToString(itemMap.get("domainUrl")), valueToString(itemMap.get("downloadUrl")), valueToString(itemMap.get("ossUrl"))));
                artifactRef.put("fileSize", itemMap.get("fileSize"));
                artifactRef.put("mimeType", valueToString(itemMap.get("mimeType")));
                artifactRef.put("missing", false);
                if (StringUtils.hasText(artifactRef.getString("displayName"))) {
                    artifactRefs.add(artifactRef);
                }
            }
        }
        return artifactRefs;
    }

    private String buildStructuredDataJson(JSONObject structuredData) {
        if (structuredData == null || structuredData.isEmpty()) {
            return null;
        }
        return JSON.toJSONString(structuredData);
    }

    private JSONObject parseAgentResponseJson(Object source) {
        if (source == null) {
            return null;
        }
        return JSON.parseObject(JSON.toJSONString(source));
    }

    /**
     * payload_json 只保留当前没有标准列归属、但读取侧仍可能需要的最小扩展信息。
     */
    private String buildExtensionPayload(JSONObject normalizedPayload) {
        if (normalizedPayload == null || normalizedPayload.isEmpty()) {
            return null;
        }
        JSONObject extensionPayload = new JSONObject(new LinkedHashMap<>());
        copyIfPresent(normalizedPayload, extensionPayload, "messageTime");
        copyIfPresent(normalizedPayload, extensionPayload, "digitalEmployee");
        copyIfPresent(normalizedPayload, extensionPayload, "errorMessage");
        copyIfPresent(normalizedPayload, extensionPayload, "agentType");
        return extensionPayload.isEmpty() ? null : extensionPayload.toJSONString();
    }

    private void copyIfPresent(JSONObject source, JSONObject target, String key) {
        if (source != null && source.containsKey(key) && source.get(key) != null) {
            target.put(key, source.get(key));
        }
    }

    private OrderedEvent buildProjectedEvent(String eventKey,
                                             int seqNo,
                                             String eventType,
                                             String eventSubType,
                                             String displayArea,
                                             String taskId,
                                             Integer taskOrder,
                                             String messageIdExt,
                                             String title,
                                             String contentText,
                                             ToolCallDescriptor toolDescriptor,
                                             boolean referenceOnly,
                                             String artifactRefsJson,
                                             String structuredDataJson,
                                             String payloadJson) {
        return OrderedEvent.builder()
                .dedupKey(eventKey)
                .seqNo(seqNo)
                .eventType(eventType)
                .eventSubType(eventSubType)
                .displayArea(displayArea)
                .taskId(taskId)
                .taskOrder(taskOrder)
                .messageIdExt(messageIdExt)
                .toolUseId(toolDescriptor == null ? null : toolDescriptor.toolUseId())
                .toolName(toolDescriptor == null ? null : toolDescriptor.toolName())
                .toolArgumentsJson(normalizeJsonString(toolDescriptor == null ? null : toolDescriptor.toolArgumentsJson()))
                .referenceOnly(referenceOnly)
                .artifactRefsJson(artifactRefsJson)
                .structuredDataJson(structuredDataJson)
                .isFinal(true)
                .title(title)
                .contentText(contentText)
                .payloadJson(payloadJson)
                .eventTime(LocalDateTime.now())
                .build();
    }

    private String normalizeJsonString(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            Object parsed = JSON.parse(json);
            return JSON.toJSONString(parsed);
        } catch (Exception ignored) {
            return json;
        }
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String stringifyToolArguments(Object toolArguments) {
        if (toolArguments == null) {
            return null;
        }
        if (toolArguments instanceof String stringValue) {
            return stringValue;
        }
        return JSON.toJSONString(toolArguments);
    }

    private boolean shouldPersistFinalDetail(AgentResponse agentResponse, String messageType) {
        if (!StringUtils.hasText(messageType)) {
            return false;
        }
        if ("agent_stream".equals(messageType)) {
            return false;
        }
        if ("deep_search".equals(messageType)) {
            String subType = resolveEventSubType(agentResponse);
            return !"extend".equals(subType);
        }
        return true;
    }

    private String extractMessageType(JSONObject payload) {
        if (payload == null) {
            return null;
        }
        String topLevelMessageType = valueToString(payload.get("messageType"));
        if (StringUtils.hasText(topLevelMessageType)) {
            return topLevelMessageType;
        }
        JSONObject resultMap = payload.getJSONObject("resultMap");
        return resultMap == null ? null : valueToString(resultMap.get("messageType"));
    }

    private String resolveFinalDisplayArea(String messageType, String eventSubType) {
        return switch (messageType) {
            case "html", "markdown", "code", "data_analysis", "ppt", "file", "knowledge", "browser" -> "workspace";
            case "deep_search" -> "deep_search.report".equals(eventSubType) ? "workspace" : "timeline";
            default -> "timeline";
        };
    }

    private String resolveEventSubType(AgentResponse resp) {
        if (resp == null || !"deep_search".equals(resp.getMessageType()) || resp.getResultMap() == null) {
            return null;
        }
        Object messageType = resp.getResultMap().get("messageType");
        return messageType == null ? null : String.valueOf(messageType);
    }

    private String resolveEventTitle(AgentResponse resp) {
        if (resp == null) {
            return null;
        }
        String messageType = resp.getMessageType();
        if ("plan".equals(messageType)) {
            return resolvePlanDisplayTitle(resp.getPlan());
        }
        if ("task".equals(messageType)) {
            return abbreviate(resp.getTask(), 50, "执行任务");
        }
        if ("tool_result".equals(messageType)) {
            return resolveToolResultTitle(resp);
        }
        if ("deep_search".equals(messageType)) {
            String subType = resolveEventSubType(resp);
            if ("report".equals(subType)) {
                return "总结完成";
            }
            if ("search".equals(subType)) {
                Map<String, Object> resultMap = resp.getResultMap();
                return resolveDeepSearchSearchTitle(extractSearchQueryText(
                        resultMap == null ? null : resultMap.get("query"),
                        resultMap == null ? null : resultMap.get("searchResult")));
            }
            return "深度搜索";
        }
        if ("html".equals(messageType) || "markdown".equals(messageType) || "code".equals(messageType) || "ppt".equals(messageType)) {
            return resolveStructuredResultTitle(resp, messageType);
        }
        if ("browser".equals(messageType)) {
            return "浏览页面";
        }
        if ("file".equals(messageType)) {
            return resolveStructuredResultTitle(resp, "生成文件");
        }
        if ("knowledge".equals(messageType)) {
            return "知识库结果";
        }
        if ("data_analysis".equals(messageType)) {
            return "数据分析";
        }
        if ("agent_stream".equals(messageType)) {
            return "总结";
        }
        if ("result".equals(messageType)) {
            return "完成";
        }
        if ("task_summary".equals(messageType)) {
            return "任务总结";
        }
        return abbreviate(extractContentText(resp), 50, messageType);
    }

    private String extractContentText(AgentResponse resp) {
        if (resp == null || !StringUtils.hasText(resp.getMessageType())) {
            return null;
        }
        return switch (resp.getMessageType()) {
            case "plan_thought" -> resp.getPlanThought();
            case "plan" -> extractPlanContentText(resp.getPlan());
            case "tool_thought" -> resp.getToolThought();
            case "task" -> resp.getTask();
            case "result", "agent_stream" -> resp.getResult();
            case "task_summary" -> resp.getTaskSummary();
            case "tool_result" -> buildToolResultText(resp);
            case "deep_search" -> buildDeepSearchText(resp);
            case "html", "markdown", "code", "ppt", "data_analysis", "browser", "file", "knowledge" -> buildStructuredContentText(resp);
            default -> null;
        };
    }

    private String resolvePlanDisplayTitle(AgentResponse.Plan plan) {
        if (plan == null) {
            return "执行计划";
        }
        return abbreviate(plan.getTitle(), 50, "执行计划");
    }

    private String extractPlanContentText(AgentResponse.Plan plan) {
        String latestCompletedStep = extractLatestPlanStep(plan, "completed");
        if (!latestCompletedStep.isBlank()) {
            return latestCompletedStep;
        }
        String currentStep = extractLatestPlanStep(plan, "in_progress");
        if (!currentStep.isBlank()) {
            return currentStep;
        }
        if (plan == null) {
            return null;
        }
        return abbreviate(plan.getTitle(), 160, "");
    }

    private String resolveToolResultTitle(AgentResponse resp) {
        AgentResponse.ToolResult toolResult = resp == null ? null : resp.getToolResult();
        if (toolResult == null || !StringUtils.hasText(toolResult.getToolName())) {
            return "工具调用";
        }
        return abbreviate(toolResult.getToolName(), 50, "工具调用");
    }

    private String resolveStructuredResultTitle(AgentResponse resp, String fallbackTitle) {
        String fileName = extractPrimaryFileName(resp == null ? null : resp.getResultMap());
        if (StringUtils.hasText(fileName)) {
            return abbreviate(fileName, 50, fallbackTitle);
        }
        Object title = resp == null || resp.getResultMap() == null ? null : resp.getResultMap().get("title");
        if (title != null && StringUtils.hasText(String.valueOf(title))) {
            return abbreviate(String.valueOf(title), 50, fallbackTitle);
        }
        return fallbackTitle;
    }

    private String extractPrimaryFileName(Map<String, Object> resultMap) {
        if (resultMap == null) {
            return null;
        }
        Object fileInfo = firstNonNull(resultMap.get("fileInfo"), resultMap.get("fileList"));
        if (fileInfo instanceof List<?> fileInfoList && !fileInfoList.isEmpty()) {
            Object first = fileInfoList.get(0);
            if (first instanceof Map<?, ?> firstMap && firstMap.get("fileName") != null) {
                return String.valueOf(firstMap.get("fileName"));
            }
        }
        return null;
    }

    private String resolveDeepSearchSearchTitle(String queryText) {
        if (!StringUtils.hasText(queryText)) {
            return "网页检索";
        }
        return abbreviate("检索：" + queryText, 50, "网页检索");
    }

    private String buildDeepSearchText(AgentResponse resp) {
        if (resp == null || resp.getResultMap() == null) {
            return null;
        }
        Object searchResultObj = resp.getResultMap().get("searchResult");
        Object answerObj = resp.getResultMap().get("answer");
        String queryText = extractSearchQueryText(resp.getResultMap().get("query"), searchResultObj);
        String answerText = answerObj == null ? "" : String.valueOf(answerObj);
        if (!queryText.isEmpty() && !answerText.isEmpty()) {
            return abbreviate(queryText, 80, "") + " " + abbreviate(answerText, 120, "");
        }
        if (!queryText.isEmpty()) {
            return abbreviate(queryText, 160, "");
        }
        if (!answerText.isEmpty()) {
            return abbreviate(answerText, 160, "");
        }
        return null;
    }

    private String extractSearchQueryText(Object queryObj, Object searchResultObj) {
        String queryText = normalizeQueryText(queryObj);
        if (searchResultObj instanceof Map<?, ?> searchResultMap) {
            String searchQueryText = normalizeQueryText(searchResultMap.get("query"));
            if (!searchQueryText.isEmpty()) {
                return searchQueryText;
            }
        }
        return queryText;
    }

    private String normalizeQueryText(Object queryObj) {
        if (queryObj == null) {
            return "";
        }
        if (queryObj instanceof List<?> queryList) {
            List<String> values = new ArrayList<>();
            for (Object item : queryList) {
                String value = normalizeQueryText(item);
                if (!value.isBlank()) {
                    values.add(value);
                }
            }
            return String.join(" ", values);
        }
        if (queryObj instanceof Map<?, ?> queryMap) {
            if (queryMap.containsKey("query")) {
                return normalizeQueryText(queryMap.get("query"));
            }
            if (queryMap.containsKey("keyword")) {
                return normalizeQueryText(queryMap.get("keyword"));
            }
            return abbreviate(JSON.toJSONString(queryMap), 120, "");
        }
        return String.valueOf(queryObj).trim();
    }

    private String buildToolResultText(AgentResponse resp) {
        AgentResponse.ToolResult toolResult = resp == null ? null : resp.getToolResult();
        if (toolResult == null) {
            return null;
        }
        String toolName = toolResult.getToolName();
        String queryText = "";
        if (toolResult.getToolParam() != null && toolResult.getToolParam().get("query") != null) {
            queryText = String.valueOf(toolResult.getToolParam().get("query"));
        }
        String preview = abbreviate(toolResult.getToolResult(), 120, "");
        return String.join(" ", Arrays.asList(
                String.valueOf(toolName == null ? "" : toolName),
                queryText,
                preview == null ? "" : preview
        )).trim();
    }

    private String buildStructuredContentText(AgentResponse resp) {
        if (resp == null || resp.getResultMap() == null || resp.getResultMap().isEmpty()) {
            return null;
        }
        for (String key : Arrays.asList("title", "task", "command", "fileName", "query", "answer", "data", "codeOutput")) {
            Object value = resp.getResultMap().get(key);
            if (value instanceof String stringValue && !stringValue.isBlank()) {
                return abbreviate(stringValue, 160, "");
            }
        }
        Object fileInfo = firstNonNull(resp.getResultMap().get("fileInfo"), resp.getResultMap().get("fileList"));
        if (fileInfo instanceof List<?> fileInfoList && !fileInfoList.isEmpty()) {
            Object first = fileInfoList.get(0);
            if (first instanceof Map<?, ?> firstMap) {
                Object fileName = firstMap.get("fileName");
                if (fileName != null) {
                    return String.valueOf(fileName);
                }
            }
        }
        return abbreviate(JSON.toJSONString(resp.getResultMap()), 160, "");
    }

    private String resolveToolResultFactKey(String sourceType,
                                            String sourceSubType,
                                            String taskId,
                                            String messageId,
                                            String toolUseId) {
        if ("deep_search".equals(sourceType) && "deep_search.search".equals(sourceSubType)) {
            return "tool_result|" + sourceType + "|" + sourceSubType + "|" + defaultString(messageId);
        }
        if (StringUtils.hasText(toolUseId)) {
            return "tool_result|" + defaultString(toolUseId) + "|" + sourceType + "|" + defaultString(sourceSubType);
        }
        return "tool_result|" + sourceType + "|" + defaultString(taskId) + "|" + defaultString(messageId);
    }

    private Integer convertToInteger(Object value) {
        if (value instanceof Integer integerValue) {
            return integerValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.intValue();
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            try {
                return Integer.parseInt(stringValue);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String extractLatestPlanStep(AgentResponse.Plan plan, String targetStatus) {
        if (plan == null || plan.getSteps() == null || plan.getSteps().isEmpty() || plan.getStepStatus() == null || plan.getStepStatus().isEmpty()) {
            return "";
        }
        int upperBound = Math.min(plan.getSteps().size(), plan.getStepStatus().size());
        for (int i = upperBound - 1; i >= 0; i--) {
            String status = plan.getStepStatus().get(i);
            String step = plan.getSteps().get(i);
            if (targetStatus.equalsIgnoreCase(status) && step != null && !step.isBlank()) {
                return step.trim();
            }
        }
        return "";
    }

    private String abbreviate(String text, int maxLen, String fallback) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        return text.length() > maxLen ? text.substring(0, maxLen) : text;
    }

    private String valueToString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private record ToolCallDescriptor(String toolUseId,
                                      String toolName,
                                      String toolArgumentsJson) {
        private boolean hasToolCall() {
            return StringUtils.hasText(toolName) || StringUtils.hasText(toolArgumentsJson);
        }
    }
}
