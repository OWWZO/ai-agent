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
import java.util.Set;

/**
 * 把消息账本和事件事实账本恢复成有序 transcript blocks。
 */
@Slf4j
@Component
public class SessionTranscriptBlockAssembler {

    private final ConversationEventFactSupport factSupport = new ConversationEventFactSupport();

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

        List<JSONObject> generatedArtifactRefs = artifactRestoreSupport.normalizeFilesToArtifactRefs(
                artifactRestoreSupport.parseFiles(message.getGeneratedFilesJson()));

        if (!CollectionUtils.isEmpty(events)) {
            List<AgentMessageEvent> orderedEvents = new ArrayList<>(events);
            orderedEvents.sort(Comparator.comparing(AgentMessageEvent::getSeqNo, Comparator.nullsLast(Integer::compareTo)));
            for (AgentMessageEvent event : orderedEvents) {
                try {
                    blocks.addAll(buildEventBlocks(message, event, aggregatedArtifactRefs));
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

        // generated_files_json 只作为 turn 级索引兜底。
        // 如果事实事件已经带回同一产物，这里不再重复塞进上下文。
        List<JSONObject> fallbackGeneratedArtifactRefs = excludeKnownArtifactRefs(
                generatedArtifactRefs,
                aggregatedArtifactRefs);
        if (!fallbackGeneratedArtifactRefs.isEmpty()) {
            aggregatedArtifactRefs.addAll(fallbackGeneratedArtifactRefs);
            blocks.add(buildArtifactReferenceBlock(
                    message,
                    null,
                    "本轮生成文件",
                    "assistant",
                    fallbackGeneratedArtifactRefs,
                    true));
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
                                                          List<JSONObject> aggregatedArtifactRefs) {
        if (!factSupport.isSemanticFactEvent(event)) {
            return List.of();
        }

        ConversationEventFactSupport.ConversationEventFact fact = factSupport.readFact(event);
        List<TranscriptContextBlock> blocks = new ArrayList<>();
        List<JSONObject> eventArtifactRefs = new ArrayList<>(fact.artifactRefs());
        if (!eventArtifactRefs.isEmpty()) {
            aggregatedArtifactRefs.addAll(eventArtifactRefs);
        }

        switch (fact.eventType()) {
            case "assistant_thought" -> appendAssistantThought(blocks, message, event, fact);
            case "plan_snapshot" -> appendPlanSnapshot(blocks, message, event, fact);
            case "tool_use" -> blocks.add(buildToolUseBlock(message, event, fact));
            case "tool_result" -> appendToolResult(blocks, message, event, fact, eventArtifactRefs);
            case "artifact_reference" -> appendArtifactReference(blocks, message, event, fact, eventArtifactRefs);
            default -> {
                return List.of();
            }
        }
        return blocks;
    }

    private void appendAssistantThought(List<TranscriptContextBlock> blocks,
                                        AgentMessage message,
                                        AgentMessageEvent event,
                                        ConversationEventFactSupport.ConversationEventFact fact) {
        if (!StringUtils.hasText(fact.contentText())) {
            return;
        }
        blocks.add(TranscriptContextBlock.builder()
                .blockType(TranscriptBlockType.ASSISTANT_THOUGHT)
                .sourceMessageId(message.getId())
                .sourceSeqNo(event.getSeqNo())
                .role("assistant")
                .text(fact.contentText())
                .referenceOnly(false)
                .build());
    }

    private void appendPlanSnapshot(List<TranscriptContextBlock> blocks,
                                    AgentMessage message,
                                    AgentMessageEvent event,
                                    ConversationEventFactSupport.ConversationEventFact fact) {
        String planSummary = StringUtil.firstNonBlank(fact.contentText(), fact.title());
        if (!StringUtils.hasText(planSummary)) {
            return;
        }
        blocks.add(TranscriptContextBlock.builder()
                .blockType(TranscriptBlockType.ASSISTANT_THOUGHT)
                .sourceMessageId(message.getId())
                .sourceSeqNo(event.getSeqNo())
                .role("assistant")
                .text(planSummary)
                .referenceOnly(false)
                .build());
    }

    private TranscriptContextBlock buildToolUseBlock(AgentMessage message,
                                                     AgentMessageEvent event,
                                                     ConversationEventFactSupport.ConversationEventFact fact) {
        return TranscriptContextBlock.builder()
                .blockType(TranscriptBlockType.TOOL_USE)
                .sourceMessageId(message.getId())
                .sourceSeqNo(event.getSeqNo())
                .role("assistant")
                .text(resolveToolUsePreview(fact))
                .toolUseId(fact.toolUseId())
                .toolName(fact.toolName())
                .toolArgumentsJson(fact.toolArgumentsJson())
                .referenceOnly(false)
                .build();
    }

    private void appendToolResult(List<TranscriptContextBlock> blocks,
                                  AgentMessage message,
                                  AgentMessageEvent event,
                                  ConversationEventFactSupport.ConversationEventFact fact,
                                  List<JSONObject> artifactRefs) {
        blocks.add(TranscriptContextBlock.builder()
                .blockType(TranscriptBlockType.TOOL_RESULT)
                .sourceMessageId(message.getId())
                .sourceSeqNo(event.getSeqNo())
                .role("tool")
                .text(resolveToolResultText(fact, artifactRefs))
                .toolUseId(fact.toolUseId())
                .toolName(fact.toolName())
                .toolArgumentsJson(fact.toolArgumentsJson())
                .resultPayloadJson(buildToolResultPayloadJson(fact, artifactRefs))
                .artifactRefs(new ArrayList<>(artifactRefs))
                .referenceOnly(fact.referenceOnly())
                .build());

        if (!artifactRefs.isEmpty()) {
            blocks.add(buildArtifactReferenceBlock(
                    message,
                    event,
                    "历史产物引用",
                    "tool",
                    artifactRefs,
                    fact.referenceOnly()));
        }
    }

    private void appendArtifactReference(List<TranscriptContextBlock> blocks,
                                         AgentMessage message,
                                         AgentMessageEvent event,
                                         ConversationEventFactSupport.ConversationEventFact fact,
                                         List<JSONObject> artifactRefs) {
        if (artifactRefs.isEmpty()) {
            return;
        }
        blocks.add(buildArtifactReferenceBlock(
                message,
                event,
                "历史产物引用",
                "assistant",
                artifactRefs,
                fact.referenceOnly()));
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

    private String resolveToolUsePreview(ConversationEventFactSupport.ConversationEventFact fact) {
        String toolName = StringUtil.firstNonBlank(fact.toolName(), fact.title(), "tool");
        String argumentsText = fact.toolArgumentsJson();
        if (StringUtils.hasText(argumentsText)) {
            return "准备调用 " + toolName + "，参数：" + StringUtil.abbreviate(argumentsText, 160);
        }
        return "准备调用 " + toolName;
    }

    private String resolveToolResultText(ConversationEventFactSupport.ConversationEventFact fact,
                                         List<JSONObject> artifactRefs) {
        String resultText = StringUtil.firstNonBlank(fact.contentText(), fact.title());
        if (StringUtils.hasText(resultText)) {
            return resultText;
        }
        if (!CollectionUtils.isEmpty(artifactRefs)) {
            return "已生成或更新产物：" + joinArtifactNames(artifactRefs);
        }
        return "已完成历史工具结果恢复";
    }

    private String buildToolResultPayloadJson(ConversationEventFactSupport.ConversationEventFact fact,
                                              List<JSONObject> artifactRefs) {
        JSONObject structuredData = factSupport.parseObject(fact.structuredData() == null
                ? null
                : fact.structuredData().toJSONString());
        if (structuredData.isEmpty()) {
            JSONObject fallback = new JSONObject(new LinkedHashMap<>());
            fallback.put("messageType", resolveResultMessageType(fact));
            if (StringUtils.hasText(fact.contentText())) {
                fallback.put("answer", fact.contentText());
            }
            fallback.put("isFinal", true);
            if (!artifactRefs.isEmpty()) {
                fallback.put("artifactRefs", artifactRefs);
            }
            return fallback.toJSONString();
        }
        if (!StringUtils.hasText(structuredData.getString("messageType"))) {
            structuredData.put("messageType", resolveResultMessageType(fact));
        }
        structuredData.putIfAbsent("isFinal", true);
        return structuredData.toJSONString();
    }

    private String resolveResultMessageType(ConversationEventFactSupport.ConversationEventFact fact) {
        String canonicalSubType = fact.eventSubType();
        if (!StringUtils.hasText(canonicalSubType)) {
            return StringUtil.firstNonBlank(fact.toolName(), "task");
        }
        int splitIndex = canonicalSubType.indexOf('.');
        return splitIndex < 0 ? canonicalSubType : canonicalSubType.substring(0, splitIndex);
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
            String key = artifactRefKey(artifactRef);
            if (!StringUtils.hasText(key) || !seen.add(key)) {
                continue;
            }
            deduplicatedRefs.add(artifactRef);
        }
        return deduplicatedRefs;
    }

    private List<JSONObject> excludeKnownArtifactRefs(List<JSONObject> candidateArtifactRefs,
                                                      List<JSONObject> knownArtifactRefs) {
        if (CollectionUtils.isEmpty(candidateArtifactRefs)) {
            return List.of();
        }

        Set<String> seen = new LinkedHashSet<>();
        for (JSONObject knownArtifactRef : knownArtifactRefs) {
            String knownKey = artifactRefKey(knownArtifactRef);
            if (StringUtils.hasText(knownKey)) {
                seen.add(knownKey);
            }
        }

        List<JSONObject> filteredArtifactRefs = new ArrayList<>();
        for (JSONObject candidateArtifactRef : candidateArtifactRefs) {
            String candidateKey = artifactRefKey(candidateArtifactRef);
            if (!StringUtils.hasText(candidateKey) || seen.contains(candidateKey)) {
                continue;
            }
            seen.add(candidateKey);
            filteredArtifactRefs.add(candidateArtifactRef);
        }
        return filteredArtifactRefs;
    }

    private String artifactRefKey(JSONObject artifactRef) {
        return StringUtil.firstNonBlank(
                artifactRef == null ? null : artifactRef.getString("resourceKey"),
                artifactRef == null ? null : artifactRef.getString("downloadUrl"),
                artifactRef == null ? null : artifactRef.getString("previewUrl"),
                artifactRef == null ? null : artifactRef.getString("displayName"));
    }
}
