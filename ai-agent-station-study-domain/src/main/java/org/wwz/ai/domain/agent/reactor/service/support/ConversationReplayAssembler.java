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
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 将消息账本与事件事实流装配为历史详情模型。
 */
@Slf4j
@Component
public class ConversationReplayAssembler {

    private final ConversationEventFactSupport factSupport = new ConversationEventFactSupport();

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
                        .generatedFiles(parseJson(message.getGeneratedFilesJson()))
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
                .map(this::toEventDetail)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private ConversationEventDetail toEventDetail(AgentMessageEvent event) {
        ConversationEventFactSupport.ProjectedHistoryEvent projected = factSupport.projectHistoryEvent(event);
        if (projected == null) {
            return null;
        }
        return ConversationEventDetail.builder()
                .seqNo(event.getSeqNo())
                .eventType(projected.eventType())
                .eventSubType(projected.eventSubType())
                .displayArea(projected.displayArea())
                .taskId(projected.taskId())
                .taskOrder(projected.taskOrder())
                .messageIdExt(projected.messageId())
                .title(event.getTitle())
                .contentText(event.getContentText())
                .payload(projected.payload())
                .isFinal(1)
                .status(event.getStatus())
                .build();
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
