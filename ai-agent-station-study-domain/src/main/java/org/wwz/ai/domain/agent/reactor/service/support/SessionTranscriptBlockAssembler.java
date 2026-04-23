package org.wwz.ai.domain.agent.reactor.service.support;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.wwz.ai.domain.agent.reactor.agent.util.StringUtil;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessage;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessageEvent;
import org.wwz.ai.domain.agent.reactor.model.memory.SessionTurnMemory;
import org.wwz.ai.domain.agent.reactor.model.memory.TranscriptBlockType;
import org.wwz.ai.domain.agent.reactor.model.memory.TranscriptContextBlock;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 把消息账本和最终事件账本恢复成有序 transcript blocks。
 */
@Slf4j
@Component
public class SessionTranscriptBlockAssembler {

    private static final Set<String> THOUGHT_EVENT_TYPES = Set.of(
            "plan_thought",
            "tool_thought",
            "plan",
            "task",
            "task_summary");

    private static final Set<String> RESULT_EVENT_TYPES = Set.of(
            "tool_result",
            "deep_search",
            "html",
            "markdown",
            "code",
            "ppt",
            "file",
            "knowledge",
            "data_analysis",
            "browser");

    @Resource
    private SessionArtifactRestoreSupport artifactRestoreSupport;

    public SessionTurnMemory buildTurnMemory(AgentMessage message,
                                             List<AgentMessageEvent> events) {
        if (message == null) {
            return SessionTurnMemory.builder().build();
        }

        List<TranscriptContextBlock> blocks = new ArrayList<>();
        List<JSONObject> aggregatedArtifactRefs = new ArrayList<>();

        blocks.add(buildUserInputBlock(message));

        List<JSONObject> uploadedArtifactRefs = artifactRestoreSupport.normalizeFilesToArtifactRefs(
                artifactRestoreSupport.parseFiles(message.getFilesJson()));
        if (!uploadedArtifactRefs.isEmpty()) {
            aggregatedArtifactRefs.addAll(uploadedArtifactRefs);
            blocks.add(buildArtifactReferenceBlock(message, null, "用户上传文件", "user", uploadedArtifactRefs, false));
        }

        ToolInvocationRegistry registry = new ToolInvocationRegistry();
        if (!CollectionUtils.isEmpty(events)) {
            List<AgentMessageEvent> orderedEvents = new ArrayList<>(events);
            orderedEvents.sort(Comparator.comparing(AgentMessageEvent::getSeqNo, Comparator.nullsLast(Integer::compareTo)));
            for (AgentMessageEvent event : orderedEvents) {
                try {
                    blocks.addAll(buildEventBlocks(message, event, registry, aggregatedArtifactRefs));
                } catch (Exception e) {
                    log.warn("恢复 transcript 事件失败，已跳过该 event messageId={}, seqNo={}, eventType={}, eventSubType={}",
                            message.getId(),
                            event == null ? null : event.getSeqNo(),
                            event == null ? null : event.getEventType(),
                            event == null ? null : event.getEventSubType(),
                            e);
                }
            }
        }

        if (StringUtils.hasText(message.getResponse())) {
            blocks.add(TranscriptContextBlock.builder()
                    .blockType(TranscriptBlockType.ASSISTANT_ANSWER)
                    .sourceMessageId(message.getId())
                    .role("assistant")
                    .text(message.getResponse())
                    .referenceOnly(false)
                    .build());
        }

        return SessionTurnMemory.builder()
                .messageId(message.getId())
                .requestId(message.getRequestId())
                .sortOrder(message.getSortOrder())
                .userMessage(message.getQuery())
                .assistantMessage(message.getResponse())
                .finalAnswer(message.getResponse())
                .artifactRefs(new ArrayList<>(deduplicateArtifactRefs(aggregatedArtifactRefs)))
                .blocks(blocks)
                .build();
    }

    private TranscriptContextBlock buildUserInputBlock(AgentMessage message) {
        return TranscriptContextBlock.builder()
                .blockType(TranscriptBlockType.USER_INPUT)
                .sourceMessageId(message.getId())
                .role("user")
                .text(message.getQuery())
                .referenceOnly(false)
                .build();
    }

    private List<TranscriptContextBlock> buildEventBlocks(AgentMessage message,
                                                          AgentMessageEvent event,
                                                          ToolInvocationRegistry registry,
                                                          List<JSONObject> aggregatedArtifactRefs) {
        List<TranscriptContextBlock> blocks = new ArrayList<>();
        JSONObject payload = ConversationEventPayloadNormalizer.normalizePayloadJson(
                event == null ? null : event.getPayloadJson());
        List<JSONObject> eventArtifactRefs = ConversationEventPayloadNormalizer.extractNormalizedArtifactRefs(payload);
        if (!eventArtifactRefs.isEmpty()) {
            aggregatedArtifactRefs.addAll(eventArtifactRefs);
        }

        String eventType = lower(event == null ? null : event.getEventType());
        String eventSubType = lower(event == null ? null : event.getEventSubType());
        ToolCallDescriptor descriptor = resolveToolCallDescriptor(message, event, payload);
        String matchedToolUseId = registry.resolveForResult(descriptor.toolUseId(), descriptor.toolName(),
                event == null ? null : event.getTaskId(), event == null ? null : event.getTaskOrder());
        if (StringUtils.hasText(matchedToolUseId)) {
            descriptor = descriptor.withToolUseId(matchedToolUseId);
        }

        if (THOUGHT_EVENT_TYPES.contains(eventType)) {
            String thoughtText = resolveThoughtText(event, payload);
            if (StringUtils.hasText(thoughtText)) {
                blocks.add(TranscriptContextBlock.builder()
                        .blockType(TranscriptBlockType.ASSISTANT_THOUGHT)
                        .sourceMessageId(message == null ? null : message.getId())
                        .sourceSeqNo(event == null ? null : event.getSeqNo())
                        .role("assistant")
                        .text(thoughtText)
                        .referenceOnly(false)
                        .build());
            }
        }

        if (descriptor.hasToolCall() && registry.shouldEmitToolUse(descriptor.toolUseId())) {
            blocks.add(TranscriptContextBlock.builder()
                    .blockType(TranscriptBlockType.TOOL_USE)
                    .sourceMessageId(message == null ? null : message.getId())
                    .sourceSeqNo(event == null ? null : event.getSeqNo())
                    .role("assistant")
                    .text(resolveToolUsePreview(event, descriptor))
                    .toolUseId(descriptor.toolUseId())
                    .toolName(descriptor.toolName())
                    .toolArgumentsJson(descriptor.toolArgumentsJson())
                    .referenceOnly(false)
                    .build());
            registry.register(descriptor.toolUseId(), descriptor.toolName(),
                    event == null ? null : event.getTaskId(), event == null ? null : event.getTaskOrder());
        }

        if (shouldEmitToolResult(eventType, eventSubType, payload, eventArtifactRefs)) {
            boolean referenceOnly = ConversationEventPayloadNormalizer.isReferenceOnly(
                    payload,
                    event == null ? null : event.getEventType(),
                    event == null ? null : event.getEventSubType(),
                    event == null ? null : event.getContentText());
            blocks.add(TranscriptContextBlock.builder()
                    .blockType(TranscriptBlockType.TOOL_RESULT)
                    .sourceMessageId(message == null ? null : message.getId())
                    .sourceSeqNo(event == null ? null : event.getSeqNo())
                    .role("tool")
                    .text(resolveToolResultText(event, eventArtifactRefs))
                    .toolUseId(descriptor.toolUseId())
                    .toolName(descriptor.toolName())
                    .resultPayloadJson(payload.isEmpty() ? null : payload.toJSONString())
                    .artifactRefs(new ArrayList<>(eventArtifactRefs))
                    .referenceOnly(referenceOnly)
                    .build());
            registry.close(descriptor.toolUseId());
        }

        if (!eventArtifactRefs.isEmpty()) {
            blocks.add(buildArtifactReferenceBlock(
                    message,
                    event,
                    "历史产物引用",
                    shouldEmitToolResult(eventType, eventSubType, payload, eventArtifactRefs) ? "tool" : "assistant",
                    eventArtifactRefs,
                    ConversationEventPayloadNormalizer.isReferenceOnly(
                            payload,
                            event == null ? null : event.getEventType(),
                            event == null ? null : event.getEventSubType(),
                            event == null ? null : event.getContentText())));
        }
        return blocks;
    }

    private boolean shouldEmitToolResult(String eventType,
                                         String eventSubType,
                                         JSONObject payload,
                                         List<JSONObject> artifactRefs) {
        if (RESULT_EVENT_TYPES.contains(eventType)) {
            return true;
        }
        if ("result".equals(eventType) || "agent_stream".equals(eventType)) {
            return false;
        }
        if (!CollectionUtils.isEmpty(artifactRefs)) {
            return true;
        }
        if (payload == null || payload.isEmpty()) {
            return false;
        }
        return StringUtils.hasText(StringUtil.firstNonBlank(
                findString(payload, "answer"),
                findString(payload, "summary"),
                findString(payload, "toolResult"),
                findString(payload, "data"),
                findString(payload, "command"),
                findString(payload, "codeOutput")))
                || "search".equals(eventSubType);
    }

    private TranscriptContextBlock buildArtifactReferenceBlock(AgentMessage message,
                                                               AgentMessageEvent event,
                                                               String prefix,
                                                               String role,
                                                               List<JSONObject> artifactRefs,
                                                               boolean referenceOnly) {
        return TranscriptContextBlock.builder()
                .blockType(TranscriptBlockType.ARTIFACT_REFERENCE)
                .sourceMessageId(message == null ? null : message.getId())
                .sourceSeqNo(event == null ? null : event.getSeqNo())
                .role(role)
                .text(prefix + "：" + joinArtifactNames(artifactRefs))
                .artifactRefs(new ArrayList<>(artifactRefs))
                .referenceOnly(referenceOnly)
                .build();
    }

    private String resolveThoughtText(AgentMessageEvent event, JSONObject payload) {
        return StringUtil.firstNonBlank(
                event == null ? null : event.getContentText(),
                findString(payload, "planThought"),
                findString(payload, "toolThought"),
                findString(payload, "task"),
                findString(payload, "title"));
    }

    private String resolveToolUsePreview(AgentMessageEvent event, ToolCallDescriptor descriptor) {
        String toolName = StringUtil.firstNonBlank(descriptor.toolName(), event == null ? null : event.getTitle(), "tool");
        String argumentsText = descriptor.toolArgumentsJson();
        if (StringUtils.hasText(argumentsText)) {
            return "准备调用 " + toolName + "，参数：" + StringUtil.abbreviate(argumentsText, 160);
        }
        return "准备调用 " + toolName;
    }

    private String resolveToolResultText(AgentMessageEvent event, List<JSONObject> artifactRefs) {
        String resultText = StringUtil.firstNonBlank(
                event == null ? null : event.getContentText(),
                event == null ? null : event.getTitle());
        if (StringUtils.hasText(resultText)) {
            return resultText;
        }
        if (!CollectionUtils.isEmpty(artifactRefs)) {
            return "已生成或更新产物：" + joinArtifactNames(artifactRefs);
        }
        return "已完成历史工具结果恢复";
    }

    private ToolCallDescriptor resolveToolCallDescriptor(AgentMessage message,
                                                         AgentMessageEvent event,
                                                         JSONObject payload) {
        String eventType = lower(event == null ? null : event.getEventType());
        String fallbackToolUseId = String.format("%s:%s",
                message == null ? "unknown" : String.valueOf(message.getId()),
                event == null || event.getSeqNo() == null ? "0" : event.getSeqNo());
        String toolUseId = StringUtil.firstNonBlank(
                findString(payload, "toolUseId"),
                findString(payload, "toolCallId"),
                findNestedString(payload, "toolCall", "id"),
                findNestedString(payload, "tool", "id"),
                fallbackToolUseId);
        String toolName = StringUtil.firstNonBlank(
                findString(payload, "toolName"),
                findNestedString(payload, "toolCall", "function", "name"),
                findNestedString(payload, "tool", "name"),
                needsFallbackToolName(eventType) ? eventType : null);
        Object toolArguments = firstNonNull(
                findRaw(payload, "toolArguments"),
                findNestedRaw(payload, "toolCall", "function", "arguments"),
                findRaw(payload, "arguments"),
                findRaw(payload, "toolParam"));
        String argumentsJson = stringifyToolArguments(toolArguments);
        return new ToolCallDescriptor(toolUseId, toolName, argumentsJson);
    }

    private boolean needsFallbackToolName(String eventType) {
        return "tool_thought".equals(eventType)
                || RESULT_EVENT_TYPES.contains(eventType);
    }

    private Object findRaw(JSONObject payload, String key) {
        for (JSONObject candidate : candidateObjects(payload)) {
            if (candidate != null && candidate.containsKey(key)) {
                return candidate.get(key);
            }
        }
        return null;
    }

    private Object findNestedRaw(JSONObject payload, String... path) {
        for (JSONObject candidate : candidateObjects(payload)) {
            Object current = candidate;
            boolean found = true;
            for (String segment : path) {
                if (!(current instanceof JSONObject currentJson) || !currentJson.containsKey(segment)) {
                    found = false;
                    break;
                }
                current = currentJson.get(segment);
            }
            if (found) {
                return current;
            }
        }
        return null;
    }

    private String findString(JSONObject payload, String key) {
        Object value = findRaw(payload, key);
        return value == null ? null : String.valueOf(value);
    }

    private String findNestedString(JSONObject payload, String... path) {
        Object value = findNestedRaw(payload, path);
        return value == null ? null : String.valueOf(value);
    }

    private List<JSONObject> candidateObjects(JSONObject payload) {
        if (payload == null) {
            return List.of();
        }
        List<JSONObject> candidates = new ArrayList<>();
        candidates.add(payload);
        JSONObject outerResultMap = payload.getJSONObject("resultMap");
        if (outerResultMap != null) {
            candidates.add(outerResultMap);
            JSONObject nestedResultMap = outerResultMap.getJSONObject("resultMap");
            if (nestedResultMap != null) {
                candidates.add(nestedResultMap);
            }
        }
        return candidates;
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

    private String joinArtifactNames(List<JSONObject> artifactRefs) {
        List<String> fileNames = new ArrayList<>();
        for (JSONObject artifactRef : artifactRefs) {
            String displayName = StringUtil.firstNonBlank(
                    artifactRef == null ? null : artifactRef.getString("displayName"),
                    artifactRef == null ? null : artifactRef.getString("resourceKey"));
            if (StringUtils.hasText(displayName)) {
                fileNames.add(displayName);
            }
        }
        return fileNames.isEmpty() ? "未命名引用" : String.join("、", fileNames);
    }

    private List<JSONObject> deduplicateArtifactRefs(List<JSONObject> artifactRefs) {
        if (CollectionUtils.isEmpty(artifactRefs)) {
            return List.of();
        }
        List<JSONObject> deduplicatedRefs = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (JSONObject artifactRef : artifactRefs) {
            String key = StringUtil.firstNonBlank(
                    artifactRef == null ? null : artifactRef.getString("resourceKey"),
                    artifactRef == null ? null : artifactRef.getString("downloadUrl"),
                    artifactRef == null ? null : artifactRef.getString("previewUrl"),
                    artifactRef == null ? null : artifactRef.getString("displayName"));
            if (!StringUtils.hasText(key) || !seen.add(key)) {
                continue;
            }
            deduplicatedRefs.add(artifactRef);
        }
        return deduplicatedRefs;
    }


    private String lower(String value) {
        return value == null ? "" : value.toLowerCase();
    }

    private record ToolCallDescriptor(String toolUseId, String toolName, String toolArgumentsJson) {
        private boolean hasToolCall() {
            return StringUtils.hasText(toolName) || StringUtils.hasText(toolArgumentsJson);
        }

        private ToolCallDescriptor withToolUseId(String newToolUseId) {
            return new ToolCallDescriptor(newToolUseId, toolName, toolArgumentsJson);
        }
    }

    private static class ToolInvocationRegistry {
        private final Map<String, ToolInvocationState> openInvocations = new LinkedHashMap<>();

        private boolean shouldEmitToolUse(String toolUseId) {
            return StringUtils.hasText(toolUseId) && !openInvocations.containsKey(toolUseId);
        }

        private void register(String toolUseId, String toolName, String taskId, Integer taskOrder) {
            if (!StringUtils.hasText(toolUseId)) {
                return;
            }
            openInvocations.put(toolUseId, new ToolInvocationState(toolUseId, toolName, taskId, taskOrder));
        }

        private String resolveForResult(String explicitToolUseId,
                                        String toolName,
                                        String taskId,
                                        Integer taskOrder) {
            if (StringUtils.hasText(explicitToolUseId) && openInvocations.containsKey(explicitToolUseId)) {
                return explicitToolUseId;
            }
            List<ToolInvocationState> states = new ArrayList<>(openInvocations.values());
            for (int i = states.size() - 1; i >= 0; i--) {
                ToolInvocationState state = states.get(i);
                boolean sameTask = StringUtils.hasText(taskId) && taskId.equals(state.taskId())
                        && (taskOrder == null || taskOrder.equals(state.taskOrder()));
                boolean sameTool = StringUtils.hasText(toolName) && toolName.equalsIgnoreCase(state.toolName());
                if (sameTask || sameTool) {
                    return state.toolUseId();
                }
            }
            return explicitToolUseId;
        }

        private void close(String toolUseId) {
            if (!StringUtils.hasText(toolUseId)) {
                return;
            }
            openInvocations.remove(toolUseId);
        }
    }

    private record ToolInvocationState(String toolUseId, String toolName, String taskId, Integer taskOrder) {
    }
}
