package org.wwz.ai.domain.agent.reactor.service.support;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.reactor.entity.AgentConversation;
import org.wwz.ai.domain.agent.reactor.model.dto.FileInformation;
import org.wwz.ai.domain.agent.reactor.model.multi.OrderedEvent;
import org.wwz.ai.domain.agent.reactor.service.IAgentMessageEventService;
import org.wwz.ai.domain.agent.reactor.service.IAgentMessageService;
import org.wwz.ai.domain.agent.reactor.mapper.IAgentConversationDao;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 统一协调 turn 和 event 的最终落库。
 */
@Component
public class PersistCoordinator {

    @Resource
    private IAgentMessageService messageService;
    @Resource
    private IAgentMessageEventService messageEventService;
    @Resource
    private IAgentConversationDao conversationDao;
    @Resource
    private SessionArtifactRestoreSupport sessionArtifactRestoreSupport;

    public void persistTurn(Long messageId,
                            AgentConversation conversation,
                            List<OrderedEvent> events,
                            String query,
                            String response,
                            String thought,
                            String status) {
        List<OrderedEvent> orderedEvents = new ArrayList<>(events == null ? List.of() : events);
        orderedEvents.sort(Comparator.comparing(OrderedEvent::getSeqNo));
        // 批量保存事件
        if (!orderedEvents.isEmpty()) {
            messageEventService.persistEvents(orderedEvents, messageId, status);
        }

        // 生成文件信息
        String generatedFilesJson = buildGeneratedFilesJson(orderedEvents);

        // 指标统计
        String metricsJson = buildMetricsJson(orderedEvents.size(), status);
        switch (status) {
            case "completed" -> messageService.completeMessage(messageId, response, metricsJson, generatedFilesJson);// 正常完成
            case "partial" -> messageService.markForceStop(messageId, response, metricsJson, generatedFilesJson);// 用户中断
            default -> messageService.markError(messageId, response, metricsJson, generatedFilesJson);// 发生错误
        }

        //更新对话信息
        conversationDao.incrementMessageCount(conversation.getId());// 消息数+1
        AgentConversation update = new AgentConversation();
        update.setId(conversation.getId());
        update.setLastMessagePreview(buildLastMessagePreview(query, response, thought));
        //新对话且默认标题时，用首条提问作为标题（截断50字）
        if (conversation.getMessageCount() != null && conversation.getMessageCount() == 0
                && "新对话".equals(conversation.getTitle())) {
            update.setTitle(query.length() > 50 ? query.substring(0, 50) + "..." : query);
        }
        conversationDao.updateById(update);
    }

    public String buildGeneratedFilesJson(List<OrderedEvent> orderedEvents) {
        if (orderedEvents == null || orderedEvents.isEmpty()) {
            return "[]";
        }

        List<JSONObject> artifactRefs = new ArrayList<>();
        for (OrderedEvent orderedEvent : orderedEvents) {
            artifactRefs.addAll(sessionArtifactRestoreSupport.parseArtifactRefs(orderedEvent.getArtifactRefsJson()));
        }
        List<FileInformation> generatedFiles = sessionArtifactRestoreSupport.toFiles(
                sessionArtifactRestoreSupport.deduplicateArtifactRefs(artifactRefs));
        return JSON.toJSONString(generatedFiles);
    }

    private String buildMetricsJson(int eventCount, String status) {
        JSONObject metrics = new JSONObject();
        metrics.put("detail_count", eventCount);
        metrics.put("event_count", eventCount);
        metrics.put("status", status);
        return metrics.toJSONString();
    }

    private String buildLastMessagePreview(String query, String response, String thought) {
        String base = query;
        if (base == null || base.isBlank()) {
            base = response;
        }
        if ((base == null || base.isBlank()) && thought != null && !thought.isBlank()) {
            base = thought;
        }
        if (base == null) {
            return null;
        }
        return base.length() > 100 ? base.substring(0, 100) + "..." : base;
    }
}
