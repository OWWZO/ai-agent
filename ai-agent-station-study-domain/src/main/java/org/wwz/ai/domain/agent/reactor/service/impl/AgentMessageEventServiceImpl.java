package org.wwz.ai.domain.agent.reactor.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessageEvent;
import org.wwz.ai.domain.agent.reactor.mapper.IAgentMessageEventDao;
import org.wwz.ai.domain.agent.reactor.model.multi.OrderedEvent;
import org.wwz.ai.domain.agent.reactor.service.IAgentMessageEventService;

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
    public void persistEvents(List<OrderedEvent> orderedEvents, Long messageId, Long conversationId,
                              String sessionId, String requestId, String finalStatus) {
        if (orderedEvents == null || orderedEvents.isEmpty()) {
            return;
        }

        List<AgentMessageEvent> events = new ArrayList<>(orderedEvents.size());
        for (OrderedEvent orderedEvent : orderedEvents) {
            LocalDateTime eventTime = orderedEvent.getEventTime() != null ? orderedEvent.getEventTime() : LocalDateTime.now();
            events.add(AgentMessageEvent.builder()
                    .messageId(messageId)
                    .conversationId(conversationId)
                    .sessionId(sessionId)
                    .requestId(requestId)
                    .seqNo(orderedEvent.getSeqNo())
                    .eventType(orderedEvent.getEventType())
                    .eventSubType(orderedEvent.getEventSubType())
                    .displayArea(StringUtils.defaultIfBlank(orderedEvent.getDisplayArea(), "timeline"))
                    .taskId(orderedEvent.getTaskId())
                    .taskOrder(orderedEvent.getTaskOrder())
                    .messageIdExt(orderedEvent.getMessageIdExt())
                    .title(resolveTitle(orderedEvent))
                    .contentText(orderedEvent.getContentText())
                    .payloadJson(orderedEvent.getPayloadJson())
                    .artifactId(null)
                    .isFinal(orderedEvent.isFinal() ? 1 : 0)
                    .status(finalStatus)
                    .startedAt(eventTime)
                    .endedAt(orderedEvent.isFinal() ? eventTime : null)
                    .deleted(0)
                    .build());
        }

        messageEventDao.batchInsert(events);
    }

    @Override
    public String buildRenderSnapshot(List<OrderedEvent> orderedEvents, String thoughtText, String multiAgentJson,
                                      String tasksJson, String planJson, String conclusionJson, String status) {
        JSONObject snapshot = new JSONObject();
        snapshot.put("v", 1);
        snapshot.put("status", status);
        if (StringUtils.isNotBlank(thoughtText)) {
            snapshot.put("thought", thoughtText);
        }
        if (StringUtils.isNotBlank(planJson)) {
            snapshot.put("plan", JSON.parseObject(planJson));
        }
        if (StringUtils.isNotBlank(tasksJson)) {
            snapshot.put("tasks", JSON.parseArray(tasksJson));
        } else if (StringUtils.isNotBlank(multiAgentJson)) {
            JSONObject multiAgent = JSON.parseObject(multiAgentJson);
            Object tasks = multiAgent.get("tasks");
            if (tasks != null) {
                snapshot.put("tasks", tasks);
            }
            Object plan = multiAgent.get("plan");
            if (plan != null && !snapshot.containsKey("plan")) {
                snapshot.put("plan", plan);
            }
        }
        if (StringUtils.isNotBlank(conclusionJson)) {
            snapshot.put("conclusion", JSON.parseObject(conclusionJson));
        } else if (StringUtils.isNotBlank(tasksJson)) {
            JSONObject derivedConclusion = extractConclusion(JSON.parseArray(tasksJson));
            if (derivedConclusion != null) {
                snapshot.put("conclusion", derivedConclusion);
            }
        }

        JSONArray timeline = new JSONArray();
        if (orderedEvents != null) {
            for (OrderedEvent orderedEvent : orderedEvents) {
                JSONObject entry = new JSONObject();
                entry.put("seq", orderedEvent.getSeqNo());
                entry.put("type", orderedEvent.getEventType());
                if (StringUtils.isNotBlank(orderedEvent.getEventSubType())) {
                    entry.put("subType", orderedEvent.getEventSubType());
                }
                entry.put("area", StringUtils.defaultIfBlank(orderedEvent.getDisplayArea(), "timeline"));
                entry.put("title", resolveTitle(orderedEvent));
                if (StringUtils.isNotBlank(orderedEvent.getContentText())) {
                    entry.put("content", abbreviate(orderedEvent.getContentText(), 200));
                }
                if (StringUtils.isNotBlank(orderedEvent.getTaskId())) {
                    entry.put("taskId", orderedEvent.getTaskId());
                }
                if (StringUtils.isNotBlank(orderedEvent.getMessageIdExt())) {
                    entry.put("messageIdExt", orderedEvent.getMessageIdExt());
                }
                entry.put("isFinal", orderedEvent.isFinal());
                timeline.add(entry);
            }
        }
        snapshot.put("timeline", timeline);
        return snapshot.toJSONString();
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

    private JSONObject extractConclusion(JSONArray tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return null;
        }

        for (int i = tasks.size() - 1; i >= 0; i--) {
            Object groupObj = tasks.get(i);
            if (!(groupObj instanceof JSONArray)) {
                continue;
            }
            JSONArray group = (JSONArray) groupObj;
            for (int j = group.size() - 1; j >= 0; j--) {
                Object taskObj = group.get(j);
                if (!(taskObj instanceof JSONObject)) {
                    continue;
                }
                JSONObject task = (JSONObject) taskObj;
                String messageType = task.getString("messageType");
                if ("result".equals(messageType) || "task_summary".equals(messageType) || "agent_stream".equals(messageType)) {
                    return task;
                }
            }
        }
        return null;
    }
}
