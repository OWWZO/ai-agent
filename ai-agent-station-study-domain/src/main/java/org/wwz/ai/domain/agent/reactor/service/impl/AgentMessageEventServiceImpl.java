package org.wwz.ai.domain.agent.reactor.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessageEvent;
import org.wwz.ai.domain.agent.reactor.mapper.IAgentMessageEventDao;
import org.wwz.ai.domain.agent.reactor.model.multi.OrderedEvent;
import org.wwz.ai.domain.agent.reactor.service.IAgentMessageEventService;
import org.wwz.ai.domain.agent.reactor.service.support.ConversationEventPayloadNormalizer;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Agent 消息事件服务实现
 */
@Service
public class AgentMessageEventServiceImpl implements IAgentMessageEventService {

    @Resource
    private IAgentMessageEventDao messageEventDao;

    @Override
    public void persistEvents(List<OrderedEvent> orderedEvents, Long messageId, String finalStatus) {
        if (orderedEvents == null || orderedEvents.isEmpty()) {
            return;
        }

        List<AgentMessageEvent> events = new ArrayList<>(orderedEvents.size());
        for (OrderedEvent orderedEvent : orderedEvents) {
            LocalDateTime eventTime = orderedEvent.getEventTime() != null ? orderedEvent.getEventTime() : LocalDateTime.now();
            events.add(AgentMessageEvent.builder()
                    .messageId(messageId)
                    .seqNo(orderedEvent.getSeqNo())
                    .eventType(orderedEvent.getEventType())
                    .eventSubType(orderedEvent.getEventSubType())
                    .displayArea(StringUtils.defaultIfBlank(orderedEvent.getDisplayArea(), "timeline"))
                    .taskId(orderedEvent.getTaskId())
                    .taskOrder(orderedEvent.getTaskOrder())
                    .messageIdExt(orderedEvent.getMessageIdExt())
                    .title(resolveTitle(orderedEvent))
                    .contentText(orderedEvent.getContentText())
                    .payloadJson(normalizePayloadJson(orderedEvent.getPayloadJson()))
                    .isFinal(orderedEvent.isFinal() ? 1 : 0)
                    .status(finalStatus)
                    .startedAt(eventTime)
                    .endedAt(orderedEvent.isFinal() ? eventTime : null)
                    .deleted(0)
                    .build());
        }

        messageEventDao.batchInsert(events);
    }

    private String resolveTitle(OrderedEvent orderedEvent) {
        if (StringUtils.isNotBlank(orderedEvent.getTitle())) {
            return orderedEvent.getTitle();
        }

        String eventType = StringUtils.defaultString(orderedEvent.getEventType());
        switch (eventType) {
            case "plan_thought":
                return "思考中";
            case "plan":
                return abbreviate(StringUtils.defaultIfBlank(orderedEvent.getContentText(), "任务计划"), 50);
            case "task":
                return abbreviate(StringUtils.defaultIfBlank(orderedEvent.getContentText(), "执行任务"), 50);
            case "deep_search":
                return resolveDeepSearchTitle(orderedEvent);
            case "html":
            case "markdown":
            case "code":
            case "ppt":
                return "正在生成" + eventType;
            case "data_analysis":
                return "数据分析";
            case "browser":
                return "浏览页面";
            case "file":
                return "生成文件";
            case "knowledge":
                return "知识库结果";
            case "tool_thought":
                return "推理中";
            case "tool_result":
                return "工具调用";
            case "agent_stream":
                return "总结";
            case "result":
                return "完成";
            case "task_summary":
                return "任务总结";
            default:
                return StringUtils.defaultIfBlank(orderedEvent.getContentText(), eventType);
        }
    }

    private String resolveDeepSearchTitle(OrderedEvent orderedEvent) {
        String subType = StringUtils.defaultString(orderedEvent.getEventSubType());
        if ("report".equals(subType)) {
            return orderedEvent.isFinal() ? "总结完成" : "正在总结";
        }
        if ("search".equals(subType)) {
            return "搜索完成";
        }
        return "正在搜索";
    }

    private String abbreviate(String text, int maxLen) {
        if (StringUtils.isBlank(text)) {
            return text;
        }
        return text.length() > maxLen ? text.substring(0, maxLen) : text;
    }

    /**
     * 事件服务统一负责 payloadJson 的最终收口，
     * 这样即便未来不是通过当前 SSE 持久化链路写入，也能保持 artifact 缺失态语义一致。
     */
    private String normalizePayloadJson(String payloadJson) {
        if (StringUtils.isBlank(payloadJson)) {
            return payloadJson;
        }

        try {
            Object payload = JSON.parse(payloadJson);
            Object normalizedPayload = ConversationEventPayloadNormalizer.normalizePayload(payload);
            return JSON.toJSONString(normalizedPayload);
        } catch (JSONException e) {
            return payloadJson;
        }
    }
}
