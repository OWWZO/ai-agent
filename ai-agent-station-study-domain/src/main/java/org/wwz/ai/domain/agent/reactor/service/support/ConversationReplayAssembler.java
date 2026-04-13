package org.wwz.ai.domain.agent.reactor.service.support;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessage;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessageEvent;
import org.wwz.ai.domain.agent.reactor.model.history.ConversationEventDetail;
import org.wwz.ai.domain.agent.reactor.model.history.ConversationTurnDetail;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 将消息账本与事件流装配为历史详情模型。
 */
@Slf4j
@Component
public class ConversationReplayAssembler {

    public List<ConversationTurnDetail> assembleTurns(List<AgentMessage> messages,
                                                      Map<Long, List<AgentMessageEvent>> eventMap) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }

        return messages.stream()
                .map(message -> ConversationTurnDetail.builder()
                        .requestId(message.getRequestId())
                        .sortOrder(message.getSortOrder())
                        .query(message.getQuery())
                        .files(parseJson(message.getFilesJson()))
                        .agentType(message.getAgentType())
                        .response(message.getResponse())
                        .status(message.getStatus())
                        .forceStop(message.getForceStop())
                        .metrics(parseJson(message.getMetricsJson()))
                        .startedAt(message.getStartedAt())
                        .finishedAt(message.getFinishedAt())
                        .events(assembleEvents(eventMap.get(message.getId())))
                        .build())
                .collect(Collectors.toList());
    }

    private List<ConversationEventDetail> assembleEvents(List<AgentMessageEvent> events) {
        if (events == null || events.isEmpty()) {
            return Collections.emptyList();
        }

        return events.stream()
                .map(event -> ConversationEventDetail.builder()
                        .seqNo(event.getSeqNo())
                        .eventType(event.getEventType())
                        .eventSubType(event.getEventSubType())
                        .displayArea(event.getDisplayArea())
                        .taskId(event.getTaskId())
                        .taskOrder(event.getTaskOrder())
                        .messageIdExt(event.getMessageIdExt())
                        .title(event.getTitle())
                        .contentText(event.getContentText())
                        .payload(normalizePayload(parseJson(event.getPayloadJson())))
                        .isFinal(event.getIsFinal())
                        .status(event.getStatus())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 统一把旧 fileInfo/fileList 兜底转换为 artifactRefs，避免前端继续感知旧字段。
     */
    private Object normalizePayload(Object payload) {
        return ConversationEventPayloadNormalizer.normalizePayload(payload);
    }

    private Object parseJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JSON.parse(json);
        } catch (JSONException e) {
            log.warn("历史详情 JSON 解析失败，回退原字符串: {}", json, e);
            return json;
        }
    }
}
