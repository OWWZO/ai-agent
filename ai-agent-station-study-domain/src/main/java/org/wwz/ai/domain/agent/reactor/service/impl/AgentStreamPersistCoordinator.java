package org.wwz.ai.domain.agent.reactor.service.impl;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.wwz.ai.domain.agent.model.valobj.FixRoleVO;
import org.wwz.ai.domain.agent.reactor.agent.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.reactor.agent.enums.AgentType;
import org.wwz.ai.domain.agent.reactor.agent.enums.ConversationAgentType;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.reactor.entity.AgentConversation;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessage;
import org.wwz.ai.domain.agent.reactor.handler.AgentResponseHandler;
import org.wwz.ai.domain.agent.reactor.mapper.IAgentConversationDao;
import org.wwz.ai.domain.agent.reactor.mapper.IAgentMessageDao;
import org.wwz.ai.domain.agent.reactor.model.dto.FileInformation;
import org.wwz.ai.domain.agent.reactor.model.memory.SessionMemoryPreparationResult;
import org.wwz.ai.domain.agent.reactor.model.memory.SessionTurnMemory;
import org.wwz.ai.domain.agent.reactor.model.memory.SessionWorkingMemory;
import org.wwz.ai.domain.agent.reactor.model.memory.TranscriptBlockType;
import org.wwz.ai.domain.agent.reactor.model.memory.TranscriptContextBlock;
import org.wwz.ai.domain.agent.reactor.model.multi.OrderedEvent;
import org.wwz.ai.domain.agent.reactor.model.multi.EventResult;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.reactor.model.req.GptQueryReq;
import org.wwz.ai.domain.agent.reactor.model.response.AgentResponse;
import org.wwz.ai.domain.agent.reactor.model.response.GptProcessResult;
import org.wwz.ai.domain.agent.reactor.service.IAgentConversationService;
import org.wwz.ai.domain.agent.reactor.service.IAgentMessageService;
import org.wwz.ai.domain.agent.reactor.service.IAgentSessionMemoryService;
import org.wwz.ai.domain.agent.reactor.service.IAgentStreamPersistService;
import org.wwz.ai.domain.agent.reactor.service.support.ActiveSessionStreamRegistry;
import org.wwz.ai.domain.agent.reactor.service.support.EventProjector;
import org.wwz.ai.domain.agent.reactor.service.support.PersistCoordinator;
import org.wwz.ai.domain.agent.reactor.service.support.SessionArtifactRestoreSupport;
import org.wwz.ai.domain.agent.reactor.service.support.StreamExecutor;
import org.wwz.ai.domain.agent.reactor.util.ChateiUtils;
import org.wwz.ai.domain.agent.reactor.util.SseUtil;
import org.wwz.ai.domain.agent.service.IFixRoleService;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 流式对话持久化协调器。
 */
@Slf4j
@Service
public class AgentStreamPersistCoordinator implements IAgentStreamPersistService {

    @Resource
    private ReactorConfig reactorConfig;
    @Resource
    private Map<AgentType, AgentResponseHandler> handlerMap;
    @Resource
    private IAgentConversationService conversationService;
    @Resource
    private IAgentMessageService messageService;
    @Resource
    private IAgentConversationDao conversationDao;
    @Resource
    private IAgentMessageDao messageDao;
    @Resource
    private IFixRoleService fixRoleService;
    @Resource
    private IAgentSessionMemoryService sessionMemoryService;
    @Resource
    private SessionArtifactRestoreSupport sessionArtifactRestoreSupport;
    @Resource
    private ActiveSessionStreamRegistry activeSessionStreamRegistry;
    @Resource
    private StreamExecutor streamExecutor;
    @Resource
    private EventProjector eventProjector;
    @Resource
    private PersistCoordinator persistCoordinator;

    @Override
    public SseEmitter sendAndPersist(String sessionId, String requestId, String deviceId,
                                     String query, Integer deepThink, String outputStyle, String filesJson,
                                     String aiAgentId) {
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
        } else {
            SessionGuardResult guardResult = validateSessionGuard(conversation, convAgentType);
            if (guardResult != null) {
                return emitGuardError(requestId, guardResult.status(), guardResult.errorMsg());
            }
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

        if (ConversationAgentType.CHAT != convAgentType) {
            SessionGuardResult guardResult = validateBusyGuard(conversation);
            if (guardResult != null) {
                return emitGuardError(requestId, guardResult.status(), guardResult.errorMsg());
            }
        }

        SessionWorkingMemory preparedWorkingMemory = null;
        if (ConversationAgentType.CHAT != convAgentType) {
            SessionMemoryPreparationResult preparationResult = sessionMemoryService.prepareForRequest(conversation);
            if (preparationResult != null && preparationResult.shouldReject()) {
                return emitGuardError(
                        requestId,
                        "context_limit_exceeded",
                        StringUtils.hasText(preparationResult.getRejectReason())
                                ? preparationResult.getRejectReason()
                                : "当前会话上下文过长且压缩失败，请稍后重试或新建会话");
            }
            preparedWorkingMemory = preparationResult == null
                    ? sessionMemoryService.rebuildWorkingMemory(conversation)
                    : preparationResult.getWorkingMemory();
            log.info("session memory preflight sessionId={}, requestId={}, decision={}, estimatedTokens={}, postCompactionTokens={}, snapshotVersionId={}, reason={}",
                    conversation.getSessionId(),
                    requestId,
                    preparationResult == null ? "UNKNOWN" : preparationResult.getDecisionType(),
                    preparationResult == null ? null : preparationResult.getEstimatedTokens(),
                    preparationResult == null ? null : preparationResult.getPostCompactionTokens(),
                    preparationResult == null ? null : preparationResult.getSnapshotVersionId(),
                    preparationResult == null ? null : preparationResult.getReason());
        }

        AgentMessage placeholderMessage = messageService.insertPlaceholder(
                conversation.getId(), requestId, query, convAgentType.getCode(), filesJson);
        Long messageId = placeholderMessage.getId();

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
        List<FileInformation> currentRequestFiles = sessionArtifactRestoreSupport.parseFiles(filesJson);
        if (convAgentType == ConversationAgentType.CHAT) {
            List<AgentMessage> recentMessages = messageService.getRecentCompleted(conversation.getId(), 10);
            agentRequest.setMessages(trimToTokenBudget(buildContextMessages(recentMessages), 8000));
        } else {
            SessionWorkingMemory workingMemory = preparedWorkingMemory == null
                    ? sessionMemoryService.rebuildWorkingMemory(conversation)
                    : preparedWorkingMemory;
            applyStructuredWorkingMemory(agentRequest, workingMemory, currentRequestFiles);
        }
        if (agentRequest.getSessionFiles() == null) {
            agentRequest.setSessionFiles(currentRequestFiles);
        }

        SseEmitter emitter = SseUtil.build(TimeUnit.HOURS.toMillis(1), traceId);
        StringBuilder responseBuffer = new StringBuilder();
        StringBuilder thoughtBuffer = new StringBuilder();
        Map<String, OrderedEvent> finalDetailEvents = new LinkedHashMap<>();
        AtomicInteger seqCounter = new AtomicInteger(1);
        List<AgentResponse> agentResponses = new ArrayList<>();
        EventResult eventResult = new EventResult();
        AgentConversation targetConversation = conversation;
        Long targetMessageId = messageId;

        streamExecutor.execute(
                agentRequest,
                sessionId,
                requestId,
                targetMessageId,
                emitter,
                activeSessionStreamRegistry,
                new StreamExecutor.StreamCallback() {
                    @Override
                    public void onHeartbeat(GptProcessResult heartbeat) throws Exception {
                        emitter.send(heartbeat);
                    }

                    @Override
                    public boolean onAgentResponse(AgentResponse agentResponse) throws Exception {
                        AgentType agentType = AgentType.fromCode(agentRequest.getAgentType());
                        AgentResponseHandler handler = handlerMap.get(agentType);
                        if (handler == null) {
                            log.error("{} no handler for agentType: {}", agentRequest.getRequestId(), agentType);
                            return false;
                        }

                        GptProcessResult result = handler.handle(agentRequest, agentResponse, agentResponses, eventResult);
                        accumulateData(agentResponse, result, responseBuffer, thoughtBuffer, finalDetailEvents, seqCounter);
                        emitter.send(result);
                        if (result != null && result.isFinished()) {
                            emitter.complete();
                            return true;
                        }
                        return false;
                    }

                    @Override
                    public void onCompleted() {
                        String response = responseBuffer.length() > 0 ? responseBuffer.toString() : null;
                        String thought = thoughtBuffer.length() > 0 ? thoughtBuffer.toString() : null;
                        persistCoordinator.persistTurn(
                                targetMessageId,
                                targetConversation,
                                new ArrayList<>(finalDetailEvents.values()),
                                query,
                                response,
                                thought,
                                "completed");
                    }

                    @Override
                    public void onError(java.io.IOException error, boolean forceStopped) {
                        log.error("stream execution failed requestId={}, forceStopped={}, error={}",
                                agentRequest.getRequestId(),
                                forceStopped,
                                error.getMessage(),
                                error);
                        String response = responseBuffer.length() > 0 ? responseBuffer.toString() : null;
                        String thought = thoughtBuffer.length() > 0 ? thoughtBuffer.toString() : null;
                        persistCoordinator.persistTurn(
                                targetMessageId,
                                targetConversation,
                                new ArrayList<>(finalDetailEvents.values()),
                                query,
                                response,
                                thought,
                                forceStopped ? "partial" : "error");
                        if (forceStopped) {
                            emitter.complete();
                        } else {
                            emitter.completeWithError(error);
                        }
                    }
                });
        return emitter;
    }

    @Override
    public boolean stop(String requestId) {
        return activeSessionStreamRegistry.requestStop(requestId);
    }

    private void accumulateData(AgentResponse agentResponse,
                                GptProcessResult result,
                                StringBuilder responseBuffer,
                                StringBuilder thoughtBuffer,
                                Map<String, OrderedEvent> finalDetailEvents,
                                AtomicInteger seqCounter) {
        //收集结果文本
        if ("result".equals(agentResponse.getMessageType()) && agentResponse.getResult() != null) {
            responseBuffer.append(agentResponse.getResult());
        }
        //收集思考过程
        if ("plan_thought".equals(agentResponse.getMessageType()) && agentResponse.getPlanThought() != null) {
            thoughtBuffer.append(agentResponse.getPlanThought());
        }
        //校验事件数据是否存在
        if (result == null || result.getResultMap() == null || !result.getResultMap().containsKey("eventData")) {
            return;
        }
        // 投影转换事件数据
        Object eventData = result.getResultMap().get("eventData");
        if (!(eventData instanceof Map<?, ?> eventDataMap)) {
            return;
        }

        @SuppressWarnings("unchecked")
        List<OrderedEvent> projectedEvents = eventProjector.project(
                agentResponse,
                new LinkedHashMap<>((Map<String, Object>) eventDataMap),
                seqCounter);
        //合并到最终事件集合（存在则更新，不存在则插入）
        for (OrderedEvent projectedEvent : projectedEvents) {
            upsertFinalDetailEvent(finalDetailEvents, projectedEvent);
        }
    }

    /**
     * 非 Chat 模式统一把事实账本恢复出的工作记忆灌入请求。
     */
    private void applyStructuredWorkingMemory(AgentRequest agentRequest,
                                              SessionWorkingMemory workingMemory,
                                              List<FileInformation> currentRequestFiles) {
        agentRequest.setHistoryDialogue(workingMemory == null ? null : workingMemory.getHistoryDialogue());
        agentRequest.setMessages(buildWorkingMemoryMessages(workingMemory));
        agentRequest.setSessionFiles(buildSessionFiles(workingMemory, currentRequestFiles));
    }

    /**
     * 历史恢复出的稳定文件优先级高于本轮新上传文件。
     */
    private List<FileInformation> buildSessionFiles(SessionWorkingMemory workingMemory,
                                                    List<FileInformation> currentRequestFiles) {
        if (workingMemory == null) {
            return currentRequestFiles;
        }
        return sessionArtifactRestoreSupport.mergeFiles(workingMemory.getRestoredFiles(), currentRequestFiles);
    }

    private List<AgentRequest.Message> buildWorkingMemoryMessages(SessionWorkingMemory workingMemory) {
        if (workingMemory == null || workingMemory.getRecentTurns() == null || workingMemory.getRecentTurns().isEmpty()) {
            return List.of();
        }

        List<AgentRequest.Message> messages = new ArrayList<>();
        for (SessionTurnMemory turn : workingMemory.getRecentTurns()) {
            if (turn.getBlocks() == null || turn.getBlocks().isEmpty()) {
                continue;
            }
            for (TranscriptContextBlock block : turn.getBlocks()) {
                AgentRequest.Message requestMessage = toWorkingMemoryMessage(block);
                if (requestMessage != null) {
                    messages.add(requestMessage);
                }
            }
        }
        return messages;
    }

    private AgentRequest.Message toWorkingMemoryMessage(TranscriptContextBlock block) {
        if (block == null || block.getBlockType() == null) {
            return null;
        }
        return switch (block.getBlockType()) {
            case USER_INPUT -> AgentRequest.Message.builder()
                    .role("user")
                    .messageType("user_input")
                    .content(block.getText())
                    .artifactRefs(block.getArtifactRefs())
                    .referenceOnly(block.getReferenceOnly())
                    .build();
            case ASSISTANT_THOUGHT -> AgentRequest.Message.builder()
                    .role("assistant")
                    .messageType("assistant_thought")
                    .content(block.getText())
                    .artifactRefs(block.getArtifactRefs())
                    .referenceOnly(block.getReferenceOnly())
                    .build();
            case TOOL_USE -> AgentRequest.Message.builder()
                    .role("assistant")
                    .messageType("tool_use")
                    .content(block.getText())
                    .toolCalls(List.of(ToolCall.builder()
                            .id(block.getToolUseId())
                            .type("function")
                            .function(ToolCall.Function.builder()
                                    .name(block.getToolName())
                                    .arguments(block.getToolArgumentsJson())
                                    .build())
                            .build()))
                    .artifactRefs(block.getArtifactRefs())
                    .referenceOnly(Boolean.FALSE)
                    .build();
            case TOOL_RESULT -> AgentRequest.Message.builder()
                    .role("tool")
                    .messageType("tool_result")
                    .content(block.getText())
                    .toolCallId(block.getToolUseId())
                    .artifactRefs(block.getArtifactRefs())
                    .referenceOnly(block.getReferenceOnly())
                    .files(block.getArtifactRefs() == null ? List.of() : sessionArtifactRestoreSupport.toFiles(block.getArtifactRefs()))
                    .build();
            case ASSISTANT_ANSWER -> AgentRequest.Message.builder()
                    .role("assistant")
                    .messageType("assistant_answer")
                    .content(block.getText())
                    .artifactRefs(block.getArtifactRefs())
                    .referenceOnly(block.getReferenceOnly())
                    .build();
            case ARTIFACT_REFERENCE -> AgentRequest.Message.builder()
                    .role(StringUtils.hasText(block.getRole()) ? block.getRole() : "assistant")
                    .messageType("artifact_reference")
                    .content(block.getText())
                    .artifactRefs(block.getArtifactRefs())
                    .referenceOnly(block.getReferenceOnly())
                    .files(block.getArtifactRefs() == null ? List.of() : sessionArtifactRestoreSupport.toFiles(block.getArtifactRefs()))
                    .build();
        };
    }

    private void upsertFinalDetailEvent(Map<String, OrderedEvent> finalDetailEvents, OrderedEvent currentEvent) {
        String eventKey = currentEvent.getDedupKey() == null ? "" : currentEvent.getDedupKey();
        OrderedEvent existingEvent = finalDetailEvents.get(eventKey);
        if (existingEvent == null) {
            finalDetailEvents.put(eventKey, currentEvent);
            return;
        }

        existingEvent.setContentText(currentEvent.getContentText());
        existingEvent.setPayloadJson(currentEvent.getPayloadJson());
        existingEvent.setTitle(currentEvent.getTitle());
        existingEvent.setEventSubType(currentEvent.getEventSubType());
        existingEvent.setDisplayArea(currentEvent.getDisplayArea());
        existingEvent.setTaskId(currentEvent.getTaskId());
        existingEvent.setTaskOrder(currentEvent.getTaskOrder());
        existingEvent.setMessageIdExt(currentEvent.getMessageIdExt());
        existingEvent.setToolUseId(currentEvent.getToolUseId());
        existingEvent.setToolName(currentEvent.getToolName());
        existingEvent.setToolArgumentsJson(currentEvent.getToolArgumentsJson());
        existingEvent.setReferenceOnly(currentEvent.isReferenceOnly());
        existingEvent.setArtifactRefsJson(currentEvent.getArtifactRefsJson());
        existingEvent.setStructuredDataJson(currentEvent.getStructuredDataJson());
        existingEvent.setFinal(currentEvent.isFinal());
        if (currentEvent.getEventTime() != null) {
            existingEvent.setEventTime(currentEvent.getEventTime());
        }
    }

    private SessionGuardResult validateSessionGuard(AgentConversation conversation,
                                                    ConversationAgentType convAgentType) {
        if (conversation == null) {
            return null;
        }
        if (!Objects.equals(conversation.getAgentType(), convAgentType.getCode())) {
            return SessionGuardResult.of(
                    "mode_conflict",
                    "当前会话已绑定 REACT/PLAN_SOLVE，请新建会话后再切换模式");
        }
        return null;
    }

    private SessionGuardResult validateBusyGuard(AgentConversation conversation) {
        if (conversation == null) {
            return null;
        }
        if (messageDao.countStreamingByConversationId(conversation.getId()) > 0) {
            return SessionGuardResult.of(
                    "session_busy",
                    "当前会话仍在执行中，请等待完成或先停止当前轮次");
        }
        return null;
    }

    private SseEmitter emitGuardError(String requestId, String status, String errorMsg) {
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

    private List<AgentRequest.Message> trimToTokenBudget(List<AgentRequest.Message> messages, int maxTokens) {
        int totalTokens = 0;
        List<AgentRequest.Message> result = new ArrayList<>();
        for (int i = messages.size() - 1; i >= 0; i--) {
            AgentRequest.Message message = messages.get(i);
            int messageTokens = (message.getContent() != null ? message.getContent().length() : 0) / 3;
            if (totalTokens + messageTokens > maxTokens) {
                break;
            }
            result.add(0, message);
            totalTokens += messageTokens;
        }
        return result;
    }

    private List<AgentRequest.Message> buildContextMessages(List<AgentMessage> recentMessages) {
        List<AgentMessage> ordered = new ArrayList<>(recentMessages);
        Collections.reverse(ordered);
        List<AgentRequest.Message> messages = new ArrayList<>();
        for (AgentMessage message : ordered) {
            messages.add(AgentRequest.Message.builder().role("user").content(message.getQuery()).build());
            if (message.getResponse() != null && !message.getResponse().isEmpty()) {
                messages.add(AgentRequest.Message.builder().role("assistant").content(message.getResponse()).build());
            }
        }
        return messages;
    }

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
        messageService.markError(placeholderMessage.getId(), errorMsg, metrics.toJSONString(), "[]");

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

    private record SessionGuardResult(String status, String errorMsg) {
        private static SessionGuardResult of(String status, String errorMsg) {
            return new SessionGuardResult(status, errorMsg);
        }
    }
}
