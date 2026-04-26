package org.wwz.ai.domain.agent.reactor.service.impl;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessageEvent;
import org.wwz.ai.domain.agent.reactor.mapper.IAgentMessageEventDao;
import org.wwz.ai.domain.agent.reactor.model.multi.OrderedEvent;
import org.wwz.ai.domain.agent.reactor.service.IAgentMessageEventService;
import org.wwz.ai.domain.agent.reactor.service.support.ConversationEventFactSupport;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * Agent 消息事件服务实现
 */
@Service
public class AgentMessageEventServiceImpl implements IAgentMessageEventService {

    private final ConversationEventFactSupport factSupport = new ConversationEventFactSupport();

    @Resource
    private IAgentMessageEventDao messageEventDao;

    @Override
    public void persistEvents(List<OrderedEvent> orderedEvents, Long messageId, String finalStatus) {
        messageEventDao.deleteByMessageId(messageId);
        if (orderedEvents == null || orderedEvents.isEmpty()) {
            return;
        }

        List<AgentMessageEvent> events = new ArrayList<>(orderedEvents.size());
        for (OrderedEvent orderedEvent : orderedEvents) {
            events.add(AgentMessageEvent.builder()
                    .messageId(messageId)
                    .seqNo(orderedEvent.getSeqNo())
                    .eventType(orderedEvent.getEventType())
                    .eventSubType(orderedEvent.getEventSubType())
                    .displayArea(StringUtils.defaultIfBlank(orderedEvent.getDisplayArea(), "timeline"))
                    .taskId(orderedEvent.getTaskId())
                    .taskOrder(orderedEvent.getTaskOrder())
                    .toolUseId(orderedEvent.getToolUseId())
                    .toolName(orderedEvent.getToolName())
                    .toolArgumentsJson(factSupport.normalizeJsonString(orderedEvent.getToolArgumentsJson()))
                    .title(resolveTitle(orderedEvent))
                    .contentText(orderedEvent.getContentText())
                    .referenceOnly(orderedEvent.isReferenceOnly())
                    .artifactRefsJson(factSupport.normalizeJsonString(orderedEvent.getArtifactRefsJson()))
                    .structuredDataJson(factSupport.normalizeJsonString(orderedEvent.getStructuredDataJson()))
                    .payloadJson(factSupport.normalizeJsonString(orderedEvent.getPayloadJson()))
                    .status(finalStatus)
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
            case "assistant_thought":
                return resolveAssistantThoughtTitle(orderedEvent);
            case "plan_snapshot":
                return resolvePlanSnapshotTitle(orderedEvent);
            case "tool_use":
                return abbreviate(StringUtils.defaultIfBlank(orderedEvent.getContentText(), "准备调用工具"), 50);
            case "tool_result":
                return resolveToolResultTitle(orderedEvent);
            case "artifact_reference":
                return abbreviate(StringUtils.defaultIfBlank(orderedEvent.getContentText(), "产物引用"), 50);
            default:
                return StringUtils.defaultIfBlank(orderedEvent.getContentText(), eventType);
        }
    }

    private String resolveAssistantThoughtTitle(OrderedEvent orderedEvent) {
        if ("tool".equalsIgnoreCase(orderedEvent.getEventSubType())) {
            return "推理中";
        }
        return "思考中";
    }

    private String resolvePlanSnapshotTitle(OrderedEvent orderedEvent) {
        if ("task".equalsIgnoreCase(orderedEvent.getEventSubType())) {
            return abbreviate(StringUtils.defaultIfBlank(orderedEvent.getContentText(), "执行任务"), 50);
        }
        return abbreviate(StringUtils.defaultIfBlank(orderedEvent.getContentText(), "执行计划"), 50);
    }

    private String resolveToolResultTitle(OrderedEvent orderedEvent) {
        String subType = StringUtils.defaultString(orderedEvent.getEventSubType());
        switch (subType) {
            case "deep_search.search":
                return "搜索完成";
            case "deep_search.report":
                return "总结完成";
            case "html.page":
            case "markdown.report":
            case "code.bundle":
            case "ppt.deck":
            case "file.output":
                return "生成文件";
            case "data_analysis.output":
                return "数据分析";
            case "browser.result":
                return "浏览页面";
            case "knowledge.answer":
                return "知识库结果";
            default:
                return abbreviate(StringUtils.defaultIfBlank(orderedEvent.getContentText(), "工具结果"), 50);
        }
    }

    private String abbreviate(String text, int maxLen) {
        if (StringUtils.isBlank(text)) {
            return text;
        }
        return text.length() > maxLen ? text.substring(0, maxLen) : text;
    }

}
