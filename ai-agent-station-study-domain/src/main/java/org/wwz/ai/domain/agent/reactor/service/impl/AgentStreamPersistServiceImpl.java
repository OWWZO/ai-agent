package org.wwz.ai.domain.agent.reactor.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.wwz.ai.domain.agent.model.valobj.FixRoleVO;
import org.wwz.ai.domain.agent.reactor.agent.enums.AgentType;
import org.wwz.ai.domain.agent.reactor.agent.enums.ConversationAgentType;
import org.wwz.ai.domain.agent.reactor.agent.enums.MessageStatus;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.reactor.handler.AgentResponseHandler;
import org.wwz.ai.domain.agent.reactor.model.multi.OrderedEvent;
import org.wwz.ai.domain.agent.reactor.model.multi.EventResult;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.reactor.model.req.GptQueryReq;
import org.wwz.ai.domain.agent.reactor.model.response.AgentResponse;
import org.wwz.ai.domain.agent.reactor.model.response.GptProcessResult;
import org.wwz.ai.domain.agent.reactor.service.IAgentConversationService;
import org.wwz.ai.domain.agent.reactor.service.IAgentMessageEventService;
import org.wwz.ai.domain.agent.reactor.service.IAgentMessageService;
import org.wwz.ai.domain.agent.reactor.service.IAgentStreamPersistService;
import org.wwz.ai.domain.agent.reactor.service.support.ConversationEventPayloadNormalizer;
import org.wwz.ai.domain.agent.service.IFixRoleService;
import org.wwz.ai.domain.agent.reactor.util.ChateiUtils;
import org.wwz.ai.domain.agent.reactor.util.SseUtil;
import org.wwz.ai.domain.agent.reactor.mapper.IAgentConversationDao;
import org.wwz.ai.domain.agent.reactor.entity.AgentConversation;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessage;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;

/**
 * 流式对话+持久化服务实现
 * 包装现有的Agent Pipeline，在SSE流中累积数据，流结束后持久化到数据库
 */
@Slf4j
@Service
public class AgentStreamPersistServiceImpl implements IAgentStreamPersistService {

    @Resource
    private ReactorConfig reactorConfig;
    @Resource
    private Map<AgentType, AgentResponseHandler> handlerMap;
    @Resource
    private IAgentConversationService conversationService;
    @Resource
    private IAgentMessageService messageService;
    @Resource
    private IAgentMessageEventService messageEventService;
    @Resource
    private IAgentConversationDao conversationDao;
    @Resource
    private IFixRoleService fixRoleService;

    @Override
    public SseEmitter sendAndPersist(String sessionId, String requestId, String deviceId,
                                     String query, Integer deepThink, String outputStyle, String filesJson,
                                     String aiAgentId) {

        // 1. 解析/创建会话
        ConversationAgentType convAgentType = ConversationAgentType.resolve(outputStyle, deepThink);
        AgentConversation conversation = conversationService.getBySessionId(sessionId);
        FixRoleVO resolvedRole = null;
        if (ConversationAgentType.CHAT == convAgentType) {
            ChatRoleResolution resolution = resolveChatRole(conversation, aiAgentId);
            if (!resolution.success()) {
                return emitChatRoleError(conversation, sessionId, requestId, deviceId, query, outputStyle,
                        convAgentType, filesJson, resolution.status(), resolution.errorMsg());
            }
            resolvedRole = resolution.role();
        }

        if (conversation == null) {
            conversation = conversationService.createConversation(
                    sessionId, deviceId, null, convAgentType.getCode(), outputStyle,
                    resolvedRole != null ? resolvedRole.getAgentId() : null,
                    resolvedRole != null ? resolvedRole.getAgentName() : null);
        } else if (resolvedRole != null && shouldBindChatRole(conversation, resolvedRole)) {
            conversation = conversationService.bindChatRole(
                    conversation, resolvedRole.getAgentId(), resolvedRole.getAgentName());
        }

        // 2. 插入占位消息
        AgentMessage placeholderMessage = messageService.insertPlaceholder(
                conversation.getId(), requestId, query, convAgentType.getCode(), filesJson);
        final Long messageId = placeholderMessage.getId();
        final Long conversationId = conversation.getId();
        final String convTitle = conversation.getTitle();
        final int currentMessageCount = conversation.getMessageCount();

        // 3. 构建Agent请求 (复用现有逻辑)
        GptQueryReq gptReq = new GptQueryReq();
        gptReq.setQuery(query);
        gptReq.setSessionId(sessionId);
        gptReq.setRequestId(requestId);
        gptReq.setDeepThink(deepThink != null ? deepThink : 0);
        gptReq.setOutputStyle(outputStyle);
        gptReq.setUser("reactor");
        gptReq.setAiAgentId(resolvedRole != null ? resolvedRole.getAgentId() : null);
        String traceId = ChateiUtils.getRequestId(gptReq);
        gptReq.setTraceId(traceId);

        AgentRequest agentRequest = buildAgentRequest(gptReq);

        // 4. 滑动窗口上下文 (仅Chat模式)
        if (convAgentType == ConversationAgentType.CHAT) {
            List<AgentMessage> recentMessages = messageService.getRecentCompleted(conversationId, 10);
            List<AgentRequest.Message> contextMessages = buildContextMessages(recentMessages);
            agentRequest.setMessages(trimToTokenBudget(contextMessages, 8000));
        }

        // 5. 创建SseEmitter
        long timeoutMillis = TimeUnit.HOURS.toMillis(1);
        final SseEmitter emitter = SseUtil.build(timeoutMillis, traceId);

        // 6. 累积缓冲区
        StringBuilder responseBuffer = new StringBuilder();
        StringBuilder thoughtBuffer = new StringBuilder();
        List<OrderedEvent> orderedEvents = new ArrayList<>();
        Map<String, OrderedEvent> bufferedEvents = new LinkedHashMap<>();
        AtomicInteger seqCounter = new AtomicInteger(1);

        // 7. 发起异步请求并处理流
        executeStreamWithPersistence(agentRequest, emitter, messageId, conversationId,
                sessionId, requestId, convTitle, currentMessageCount, query,
                responseBuffer, thoughtBuffer, orderedEvents, bufferedEvents, seqCounter);

        return emitter;
    }

    private void executeStreamWithPersistence(AgentRequest autoReq, SseEmitter sseEmitter,
                                               Long messageId, Long conversationId,
                                               String sessionId, String requestId,
                                               String convTitle, int currentMessageCount,
                                               String query,
                                               StringBuilder responseBuffer,
                                               StringBuilder thoughtBuffer,
                                               List<OrderedEvent> orderedEvents,
                                               Map<String, OrderedEvent> bufferedEvents,
                                               AtomicInteger seqCounter) {
        Request request = buildHttpRequest(autoReq);

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(reactorConfig.getSseClientReadTimeout(), TimeUnit.SECONDS)
                .writeTimeout(1800, TimeUnit.SECONDS)
                .callTimeout(reactorConfig.getSseClientConnectTimeout(), TimeUnit.SECONDS)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.error("onFailure requestId={}, error={}", autoReq.getRequestId(), e.getMessage(), e);
                try {
                    persistTurnAndEvents(messageId, conversationId, query, currentMessageCount,
                            convTitle, responseBuffer, thoughtBuffer, orderedEvents, bufferedEvents, "error");
                } catch (Exception ex) {
                    log.error("持久化错误状态失败", ex);
                } finally {
                    sseEmitter.completeWithError(e);
                }
            }

            @Override
            public void onResponse(Call call, Response response) {
                List<AgentResponse> agentRespList = new ArrayList<>();
                EventResult eventResult = new EventResult();
                ResponseBody responseBody = response.body();

                if (responseBody == null) {
                    log.error("{} empty response body", autoReq.getRequestId());
                    persistTurnAndEvents(messageId, conversationId, query, currentMessageCount,
                            convTitle, responseBuffer, thoughtBuffer, orderedEvents, bufferedEvents, "error");
                    sseEmitter.completeWithError(new IllegalStateException("empty response body"));
                    return;
                }

                boolean streamCompleted = false;

                try {
                    if (!response.isSuccessful()) {
                        log.error("{} response failed: {}", autoReq.getRequestId(), responseBody.string());
                        persistTurnAndEvents(messageId, conversationId, query, currentMessageCount,
                                convTitle, responseBuffer, thoughtBuffer, orderedEvents, bufferedEvents, "error");
                        sseEmitter.completeWithError(new IllegalStateException("upstream response failed"));
                        return;
                    }

                    String line;
                    BufferedReader reader = new BufferedReader(new InputStreamReader(responseBody.byteStream()));

                    while ((line = reader.readLine()) != null) {
                        if (!line.startsWith("data:")) continue;
                        String data = line.substring(5);

                        if (data.equals("[DONE]")) {
                            log.info("{} stream [DONE]", autoReq.getRequestId());
                            streamCompleted = true;
                            break;
                        }

                        if (data.startsWith("heartbeat")) {
                            GptProcessResult heartbeat = buildHeartbeatData(autoReq.getRequestId());
                            sseEmitter.send(heartbeat);
                            continue;
                        }

                        // 解析并处理Agent响应
                        AgentResponse agentResponse = JSON.parseObject(data, AgentResponse.class);
                        AgentType agentType = AgentType.fromCode(autoReq.getAgentType());
                        AgentResponseHandler handler = handlerMap.get(agentType);
                        if (handler == null) {
                            log.error("{} no handler for agentType: {}", autoReq.getRequestId(), agentType);
                            continue;
                        }

                        GptProcessResult result = handler.handle(autoReq, agentResponse, agentRespList, eventResult);

                        // --- 累积数据 ---
                        accumulateData(agentResponse, result, responseBuffer, thoughtBuffer, orderedEvents, bufferedEvents, seqCounter);

                        // 发送给前端
                        sseEmitter.send(result);

                        if (result.isFinished()) {
                            log.info("{} task finished", autoReq.getRequestId());
                            streamCompleted = true;
                            sseEmitter.complete();
                            break;
                        }
                    }
                } catch (Exception e) {
                    log.error("{} stream exception", autoReq.getRequestId(), e);
                    try {
                        persistTurnAndEvents(messageId, conversationId, query, currentMessageCount,
                                convTitle, responseBuffer, thoughtBuffer, orderedEvents, bufferedEvents, "error");
                    } catch (Exception ex) {
                        log.error("持久化错误状态失败", ex);
                    } finally {
                        sseEmitter.completeWithError(e);
                    }
                    return;
                }

                // --- 流结束: 持久化完整消息 ---
                try {
                    if (streamCompleted) {
                        persistTurnAndEvents(messageId, conversationId, query, currentMessageCount,
                                convTitle, responseBuffer, thoughtBuffer, orderedEvents, bufferedEvents, "completed");
                        log.info("消息持久化完成 messageId={}, conversationId={}", messageId, conversationId);
                    } else {
                        persistTurnAndEvents(messageId, conversationId, query, currentMessageCount,
                                convTitle, responseBuffer, thoughtBuffer, orderedEvents, bufferedEvents, "error");
                        sseEmitter.completeWithError(new IllegalStateException("stream closed before completion"));
                    }
                } catch (Exception e) {
                    log.error("流结束持久化失败 messageId={}", messageId, e);
                    sseEmitter.completeWithError(e);
                }
            }
        });
    }

    /**
     * 累积SSE流中的数据用于最终持久化
     */
    private void accumulateData(AgentResponse agentResponse, GptProcessResult result,
                                 StringBuilder responseBuffer, StringBuilder thoughtBuffer,
                                 List<OrderedEvent> orderedEvents, Map<String, OrderedEvent> bufferedEvents,
                                 AtomicInteger seqCounter) {
        // Chat模式: 累积纯文本响应
        if ("result".equals(agentResponse.getMessageType()) && agentResponse.getResult() != null) {
            responseBuffer.append(agentResponse.getResult());
        }

        // 深度思考模式: 累积推理文本
        if ("plan_thought".equals(agentResponse.getMessageType()) && agentResponse.getPlanThought() != null) {
            thoughtBuffer.append(agentResponse.getPlanThought());
        }

        if (result == null || result.getResultMap() == null || !result.getResultMap().containsKey("eventData")) {
            return;
        }

        Object eventData = result.getResultMap().get("eventData");
        if (!(eventData instanceof Map)) {
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> eventDataMap = (Map<String, Object>) eventData;
        String rawMessageType = agentResponse.getMessageType();
        if (rawMessageType == null || rawMessageType.isEmpty()) {
            return;
        }

        OrderedEvent currentEvent = OrderedEvent.builder()
                .seqNo(seqCounter.getAndIncrement())
                .eventType(rawMessageType)
                .eventSubType(resolveEventSubType(agentResponse))
                .displayArea(resolveDisplayArea(rawMessageType))
                .taskId((String) eventDataMap.get("taskId"))
                .taskOrder(convertToInteger(eventDataMap.get("taskOrder")))
                .messageIdExt(agentResponse.getMessageId())
                .isFinal(Boolean.TRUE.equals(agentResponse.getIsFinal()))
                .title(resolveEventTitle(agentResponse))
                .contentText(extractContentText(agentResponse))
                // 持久化时统一收敛 artifactRefs，避免历史回放继续依赖旧 fileInfo/fileList 结构。
                .payloadJson(ConversationEventPayloadNormalizer.normalizePayload(eventDataMap).toJSONString())
                .eventTime(LocalDateTime.now())
                .build();

        if (shouldAggregateEvent(rawMessageType)) {
            mergeBufferedEvent(currentEvent, bufferedEvents);
            return;
        }

        orderedEvents.add(currentEvent);
    }

    private void persistTurnAndEvents(Long messageId, Long conversationId, String query,
                                          int currentMessageCount, String convTitle,
                                          StringBuilder responseBuffer, StringBuilder thoughtBuffer,
                                          List<OrderedEvent> orderedEvents, Map<String, OrderedEvent> bufferedEvents,
                                          String status) {
        List<OrderedEvent> finalOrderedEvents = mergeOrderedEvents(orderedEvents, bufferedEvents);
        if (!finalOrderedEvents.isEmpty()) {
            messageEventService.persistEvents(finalOrderedEvents, messageId, status);
        }

        JSONObject metrics = new JSONObject();
        metrics.put("event_count", finalOrderedEvents.size());
        metrics.put("status", status);
        String metricsJson = metrics.toJSONString();

        String response = responseBuffer.length() > 0 ? responseBuffer.toString() : null;

        switch (status) {
            case "completed":
                messageService.completeMessage(messageId, response, metricsJson);
                break;
            case "partial":
                messageService.markForceStop(messageId, response, metricsJson);
                break;
            case "error":
            default:
                messageService.markError(messageId, response, metricsJson);
                break;
        }

        conversationDao.incrementMessageCount(conversationId);

        AgentConversation update = new AgentConversation();
        update.setId(conversationId);
        update.setLastMessagePreview(buildLastMessagePreview(
                query,
                response,
                thoughtBuffer.length() > 0 ? thoughtBuffer.toString() : null));
        if (currentMessageCount == 0 && "新对话".equals(convTitle)) {
            update.setTitle(query.length() > 50 ? query.substring(0, 50) + "..." : query);
        }
        conversationDao.updateById(update);
    }

    private boolean shouldAggregateEvent(String messageType) {
        return Arrays.asList(
                "plan_thought",
                "tool_thought",
                "agent_stream",
                "result",
                "deep_search",
                "html",
                "markdown",
                "code",
                "data_analysis",
                "ppt"
        ).contains(messageType);
    }

    private void mergeBufferedEvent(OrderedEvent currentEvent, Map<String, OrderedEvent> bufferedEvents) {
        String eventKey = buildBufferedEventKey(currentEvent);
        OrderedEvent existingEvent = bufferedEvents.get(eventKey);
        if (existingEvent == null) {
            bufferedEvents.put(eventKey, currentEvent);
            return;
        }

        existingEvent.setContentText(mergeEventContent(existingEvent.getContentText(), currentEvent.getContentText()));
        existingEvent.setPayloadJson(currentEvent.getPayloadJson());
        existingEvent.setTitle(currentEvent.getTitle());
        existingEvent.setTaskOrder(currentEvent.getTaskOrder());
        existingEvent.setFinal(currentEvent.isFinal());
        if (currentEvent.getEventTime() != null) {
            existingEvent.setEventTime(currentEvent.getEventTime());
        }
    }

    private String buildBufferedEventKey(OrderedEvent event) {
        return String.join("|",
                defaultString(event.getEventType()),
                defaultString(event.getEventSubType()),
                defaultString(event.getTaskId()),
                defaultString(event.getMessageIdExt()));
    }

    private List<OrderedEvent> mergeOrderedEvents(List<OrderedEvent> orderedEvents, Map<String, OrderedEvent> bufferedEvents) {
        List<OrderedEvent> mergedEvents = new ArrayList<>();
        if (orderedEvents != null) {
            mergedEvents.addAll(orderedEvents);
        }
        if (bufferedEvents != null && !bufferedEvents.isEmpty()) {
            mergedEvents.addAll(bufferedEvents.values());
        }
        mergedEvents.sort(Comparator.comparing(OrderedEvent::getSeqNo));
        return mergedEvents;
    }

    private String mergeEventContent(String existingText, String currentText) {
        if (currentText == null || currentText.isBlank()) {
            return existingText;
        }
        if (existingText == null || existingText.isBlank()) {
            return currentText;
        }
        if (currentText.equals(existingText)) {
            return existingText;
        }
        if (currentText.startsWith(existingText)) {
            return currentText;
        }
        if (existingText.startsWith(currentText) || existingText.endsWith(currentText)) {
            return existingText;
        }
        return existingText + currentText;
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private String resolveDisplayArea(String messageType) {
        switch (messageType) {
            case "html":
            case "markdown":
            case "code":
            case "data_analysis":
            case "ppt":
                return "workspace_realtime";
            case "browser":
                return "workspace_browser";
            case "file":
            case "knowledge":
                return "workspace_file";
            default:
                return "timeline";
        }
    }

    private String resolveEventSubType(AgentResponse resp) {
        if ("deep_search".equals(resp.getMessageType()) && resp.getResultMap() != null) {
            Object messageType = resp.getResultMap().get("messageType");
            return messageType == null ? null : String.valueOf(messageType);
        }
        return null;
    }

    private String resolveEventTitle(AgentResponse resp) {
        String messageType = resp.getMessageType();
        if ("plan_thought".equals(messageType)) {
            return "思考中";
        }
        if ("plan".equals(messageType)) {
            return resolvePlanTitle(resp.getPlan());
        }
        if ("task".equals(messageType)) {
            return abbreviate(resp.getTask(), 50, "执行任务");
        }
        if ("tool_thought".equals(messageType)) {
            return "推理中";
        }
        if ("tool_result".equals(messageType)) {
            return "工具调用";
        }
        if ("deep_search".equals(messageType)) {
            String subType = resolveEventSubType(resp);
            if ("report".equals(subType)) {
                return Boolean.TRUE.equals(resp.getIsFinal()) ? "总结完成" : "正在总结";
            }
            if ("search".equals(subType)) {
                return "搜索完成";
            }
            return "正在搜索";
        }
        if ("html".equals(messageType) || "markdown".equals(messageType) || "code".equals(messageType) || "ppt".equals(messageType)) {
            return "正在生成" + messageType;
        }
        if ("browser".equals(messageType)) {
            return "浏览页面";
        }
        if ("file".equals(messageType)) {
            return "生成文件";
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
        switch (resp.getMessageType()) {
            case "plan_thought":
                return resp.getPlanThought();
            case "plan":
                return extractPlanContentText(resp.getPlan());
            case "tool_thought":
                return resp.getToolThought();
            case "task":
                return resp.getTask();
            case "result":
            case "agent_stream":
                return resp.getResult();
            case "task_summary":
                return resp.getTaskSummary();
            case "tool_result":
                return buildToolResultText(resp);
            case "deep_search":
                return buildDeepSearchText(resp);
            case "html":
            case "markdown":
            case "code":
            case "ppt":
            case "data_analysis":
            case "browser":
            case "file":
            case "knowledge":
                return buildStructuredContentText(resp);
            default:
                return null;
        }
    }

    private String resolvePlanTitle(AgentResponse.Plan plan) {
        String latestCompletedStep = extractLatestPlanStep(plan, "completed");
        if (!latestCompletedStep.isBlank()) {
            return abbreviate(latestCompletedStep, 50, "任务计划");
        }

        String currentStep = extractLatestPlanStep(plan, "in_progress");
        if (!currentStep.isBlank()) {
            return abbreviate(currentStep, 50, "任务计划");
        }

        if (plan != null) {
            return abbreviate(plan.getTitle(), 50, "任务计划");
        }
        return "任务计划";
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

    // 优先提取任务执行阶段里最近一次完成/进行中的步骤，避免历史标题被笼统写成“任务计划”。
    private String extractLatestPlanStep(AgentResponse.Plan plan, String targetStatus) {
        if (plan == null || plan.getSteps() == null || plan.getSteps().isEmpty()) {
            return "";
        }

        List<String> steps = plan.getSteps();
        List<String> stepStatus = plan.getStepStatus();
        if (stepStatus == null || stepStatus.isEmpty()) {
            return "";
        }

        int upperBound = Math.min(steps.size(), stepStatus.size());
        for (int i = upperBound - 1; i >= 0; i--) {
            String status = stepStatus.get(i);
            String step = steps.get(i);
            if (targetStatus.equalsIgnoreCase(status) && step != null && !step.isBlank()) {
                return step.trim();
            }
        }
        return "";
    }

    private String buildDeepSearchText(AgentResponse resp) {
        if (resp.getResultMap() == null) {
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
        if (searchResultObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> searchResultMap = (Map<String, Object>) searchResultObj;
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
        if (queryObj instanceof List) {
            List<?> queryList = (List<?>) queryObj;
            List<String> values = new ArrayList<>();
            for (Object item : queryList) {
                String value = normalizeQueryText(item);
                if (!value.isBlank()) {
                    values.add(value);
                }
            }
            return String.join(" ", values);
        }
        if (queryObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> queryMap = (Map<String, Object>) queryObj;
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
        AgentResponse.ToolResult toolResult = resp.getToolResult();
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
        if (resp.getResultMap() == null || resp.getResultMap().isEmpty()) {
            return null;
        }

        for (String key : Arrays.asList("title", "task", "command", "fileName", "name", "query", "answer", "data", "codeOutput")) {
            Object value = resp.getResultMap().get(key);
            if (value instanceof String && !((String) value).isBlank()) {
                return abbreviate((String) value, 160, "");
            }
        }

        Object fileInfo = resp.getResultMap().get("fileInfo");
        if (fileInfo instanceof List && !((List<?>) fileInfo).isEmpty()) {
            Object first = ((List<?>) fileInfo).get(0);
            if (first instanceof Map) {
                Object fileName = ((Map<?, ?>) first).get("fileName");
                if (fileName != null) {
                    return String.valueOf(fileName);
                }
            }
        }

        return abbreviate(JSON.toJSONString(resp.getResultMap()), 160, "");
    }

    private Integer convertToInteger(Object value) {
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String && !((String) value).isBlank()) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
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

    private String abbreviate(String text, int maxLen, String fallback) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        return text.length() > maxLen ? text.substring(0, maxLen) : text;
    }

    /**
     * Token预算裁剪：从最新消息往前保留，超出预算截断
     */
    private List<AgentRequest.Message> trimToTokenBudget(List<AgentRequest.Message> messages, int maxTokens) {
        int totalTokens = 0;
        List<AgentRequest.Message> result = new ArrayList<>();
        // 从最新(尾部)往前遍历
        for (int i = messages.size() - 1; i >= 0; i--) {
            AgentRequest.Message msg = messages.get(i);
            int msgTokens = (msg.getContent() != null ? msg.getContent().length() : 0) / 3; // 粗估
            if (totalTokens + msgTokens > maxTokens) break;
            result.add(0, msg);
            totalTokens += msgTokens;
        }
        return result;
    }

    /**
     * 构建滑动窗口上下文消息列表
     */
    private List<AgentRequest.Message> buildContextMessages(List<AgentMessage> recentMessages) {
        // 按sortOrder倒序查出的，需要反转为正序
        List<AgentMessage> ordered = new ArrayList<>(recentMessages);
        Collections.reverse(ordered);

        List<AgentRequest.Message> messages = new ArrayList<>();
        for (AgentMessage msg : ordered) {
            messages.add(AgentRequest.Message.builder().role("user").content(msg.getQuery()).build());
            if (msg.getResponse() != null && !msg.getResponse().isEmpty()) {
                messages.add(AgentRequest.Message.builder().role("assistant").content(msg.getResponse()).build());
            }
        }
        return messages;
    }

    /**
     * 复用现有的AgentRequest构建逻辑
     */
    private AgentRequest buildAgentRequest(GptQueryReq req) {
        AgentRequest request = new AgentRequest();
        request.setRequestId(req.getTraceId());
        request.setSessionId(req.getSessionId());
        request.setErp(req.getUser());
        request.setQuery(req.getQuery());
        request.setAiAgentId(req.getAiAgentId());

        if ("chat".equalsIgnoreCase(req.getOutputStyle())) {
            request.setAgentType(AgentType.WORKFLOW.getValue());
            request.setSopPrompt("");
        } else {
            Integer agentType = (req.getDeepThink() == null || req.getDeepThink() == 0)
                    ? AgentType.REACT.getValue()
                    : AgentType.PLAN_SOLVE.getValue();
            request.setAgentType(agentType);
            request.setSopPrompt(agentType.equals(AgentType.PLAN_SOLVE.getValue()) ? reactorConfig.getReactorSopPrompt() : "");
            request.setBasePrompt(agentType.equals(AgentType.REACT.getValue()) ? reactorConfig.getReactorBasePrompt() : "");
        }

        request.setIsStream(true);
        request.setOutputStyle(req.getOutputStyle());
        return request;
    }

    private ChatRoleResolution resolveChatRole(AgentConversation conversation, String requestedAiAgentId) {
        if (conversation != null && StringUtils.hasText(conversation.getAiAgentId())) {
            if (StringUtils.hasText(requestedAiAgentId) && !conversation.getAiAgentId().equals(requestedAiAgentId)) {
                return ChatRoleResolution.error("roleSwitchRejected", "当前会话已绑定其他角色，请新建对话后再切换角色");
            }

            FixRoleVO boundRole = fixRoleService.queryRole(conversation.getAiAgentId());
            if (boundRole == null) {
                return ChatRoleResolution.error("roleUnavailable", "当前角色已不可继续使用，请新建对话后重新选择角色");
            }
            return ChatRoleResolution.success(boundRole);
        }

        if (StringUtils.hasText(requestedAiAgentId)) {
            FixRoleVO requestedRole = fixRoleService.queryRole(requestedAiAgentId);
            if (requestedRole == null) {
                return ChatRoleResolution.error("roleUnavailable", "当前角色已不可继续使用，请新建对话后重新选择角色");
            }
            return ChatRoleResolution.success(requestedRole);
        }

        FixRoleVO defaultRole = fixRoleService.queryDefaultRole();
        if (defaultRole == null) {
            return ChatRoleResolution.error("noAvailableChatRole", "当前暂无可用角色，请稍后重试");
        }
        return ChatRoleResolution.success(defaultRole);
    }

    private boolean shouldBindChatRole(AgentConversation conversation, FixRoleVO resolvedRole) {
        if (conversation == null || resolvedRole == null) {
            return false;
        }
        if (!StringUtils.hasText(conversation.getAiAgentId())) {
            return true;
        }
        return !StringUtils.hasText(conversation.getAiAgentNameSnapshot());
    }

    private SseEmitter emitChatRoleError(AgentConversation conversation, String sessionId, String requestId,
                                         String deviceId, String query, String outputStyle,
                                         ConversationAgentType convAgentType, String filesJson,
                                         String status, String errorMsg) {
        AgentConversation targetConversation = conversation;
        if (targetConversation == null) {
            targetConversation = conversationService.createConversation(
                    sessionId, deviceId, null, convAgentType.getCode(), outputStyle, null, null);
        }

        AgentMessage placeholderMessage = messageService.insertPlaceholder(
                targetConversation.getId(), requestId, query, convAgentType.getCode(), filesJson);
        JSONObject metrics = new JSONObject();
        metrics.put("status", status);
        metrics.put("role_error", true);
        messageService.markError(placeholderMessage.getId(), errorMsg, metrics.toJSONString());

        conversationDao.incrementMessageCount(targetConversation.getId());
        updateConversationSnapshot(targetConversation, query, errorMsg);

        GptProcessResult result = GptProcessResult.builder()
                .status(status)
                .response("")
                .responseAll("")
                .finished(true)
                .responseType("markdown")
                .traceId(requestId)
                .reqId(requestId)
                .encrypted(false)
                .packageType("result")
                .errorMsg(errorMsg)
                .build();

        SseEmitter emitter = SseUtil.build(TimeUnit.MINUTES.toMillis(5), requestId);
        try {
            emitter.send(result);
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }

    private void updateConversationSnapshot(AgentConversation conversation, String query, String response) {
        AgentConversation update = new AgentConversation();
        update.setId(conversation.getId());
        update.setLastMessagePreview(buildLastMessagePreview(query, response, null));
        if (conversation.getMessageCount() != null && conversation.getMessageCount() == 0
                && "新对话".equals(conversation.getTitle())) {
            update.setTitle(query.length() > 50 ? query.substring(0, 50) + "..." : query);
        }
        conversationDao.updateById(update);
    }

    private record ChatRoleResolution(FixRoleVO role, String status, String errorMsg) {

        private static ChatRoleResolution success(FixRoleVO role) {
            return new ChatRoleResolution(role, null, null);
        }

        private static ChatRoleResolution error(String status, String errorMsg) {
            return new ChatRoleResolution(null, status, errorMsg);
        }

        private boolean success() {
            return role != null;
        }
    }

    private Request buildHttpRequest(AgentRequest autoReq) {
        String url = "http://127.0.0.1:8080/AutoAgent";
        RequestBody body = RequestBody.create(
                MediaType.parse("application/json"),
                JSONObject.toJSONString(autoReq)
        );
        return new Request.Builder().url(url).post(body).build();
    }

    private GptProcessResult buildHeartbeatData(String requestId) {
        GptProcessResult result = new GptProcessResult();
        result.setFinished(false);
        result.setStatus("success");
        result.setResponse("");
        result.setResponseAll("");
        result.setUseTimes(0);
        result.setUseTokens(0);
        result.setReqId(requestId);
        result.setPackageType("heartbeat");
        result.setEncrypted(false);
        return result;
    }
}
