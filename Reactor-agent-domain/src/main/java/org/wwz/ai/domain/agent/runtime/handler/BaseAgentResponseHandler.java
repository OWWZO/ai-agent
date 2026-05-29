package org.wwz.ai.domain.agent.runtime.handler;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.enums.ResponseTypeEnum;
import org.wwz.ai.domain.agent.reactor.model.multi.EventResult;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.reactor.model.response.AgentResponse;
import org.wwz.ai.domain.agent.reactor.model.response.GptProcessResult;
import org.wwz.ai.domain.agent.ledger.model.replay.ProjectedReplayEvent;
import org.wwz.ai.domain.agent.ledger.replay.ReplayProjector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.wwz.ai.domain.agent.reactor.model.constant.Constants.RUNNING;
import static org.wwz.ai.domain.agent.reactor.model.constant.Constants.SUCCESS;


@Slf4j
public class BaseAgentResponseHandler {
    private final ReplayProjector replayProjector;

    protected BaseAgentResponseHandler(ReplayProjector replayProjector) {
        this.replayProjector = replayProjector;
    }

    protected GptProcessResult buildCanonicalIncrResult(AgentRequest request, EventResult eventResult, AgentResponse agentResponse) {
        GptProcessResult streamResult = buildIncrResult(request, eventResult, agentResponse);
        return streamResult;
    }

    protected GptProcessResult buildIncrResult(AgentRequest request, EventResult eventResult, AgentResponse agentResponse) {
        GptProcessResult streamResult = new GptProcessResult();
        streamResult.setResponseType(ResponseTypeEnum.text.name());
        streamResult.setStatus(agentResponse.getFinish() ? SUCCESS : RUNNING);
        streamResult.setFinished(agentResponse.getFinish());
        if ("result".equals(agentResponse.getMessageType())) {
            streamResult.setResponse(agentResponse.getResult());
            streamResult.setResponseAll(agentResponse.getResult());
        }
        streamResult.setReqId(request.getRequestId());

        String agentType = (Objects.nonNull(agentResponse.getResultMap())
                && agentResponse.getResultMap().containsKey("agentType"))
                ? String.valueOf(agentResponse.getResultMap().get("agentType")) : null;

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("agentType", agentType);
        resultMap.put("multiAgent", new HashMap<>());
        ProjectedReplayEvent projectedEvent = buildProjectedEvent(eventResult, agentResponse);
        if (projectedEvent == null) {
            resultMap.put("eventData", new HashMap<>());
            streamResult.setResultMap(resultMap);
            return streamResult;
        }

        GptProcessResult projectedFrame = replayProjector.projectFrame(
                request.getRequestId(),
                projectedEvent,
                agentResponse.getFinish() != null ? agentResponse.getFinish() : streamResult.isFinished(),
                streamResult.getStatus()
        );
        if (projectedFrame.getResultMap() != null
                && projectedFrame.getResultMap().containsKey("eventData")) {
            // 实时链路只复用 projector 收口后的 eventData，不能把 history 的顶层 agentType 覆盖回实时结果。
            resultMap.put("eventData", projectedFrame.getResultMap().get("eventData"));
        }
        streamResult.setResultMap(resultMap);
        return streamResult;
    }

    private ProjectedReplayEvent buildProjectedEvent(EventResult eventResult, AgentResponse agentResponse) {
        boolean isFinal = Boolean.TRUE.equals(agentResponse.getIsFinal());
        boolean isFilterFinal = (Objects.nonNull(agentResponse.getResultMap())
                && "deep_search".equals(agentResponse.getMessageType())
                && agentResponse.getResultMap().containsKey("messageType")
                && Objects.equals(agentResponse.getResultMap().get("messageType"), "extend"));
        syncPlannerRoundId(eventResult, agentResponse);
        Map<String, Object> payload = buildAgentResponsePayload(agentResponse);
        if (payload == null) {
            return null;
        }
        appendPlannerRoundId(payload, eventResult == null ? null : eventResult.getPlannerRoundId());

        switch (agentResponse.getMessageType()) {
            case "plan_thought":
                if (isFinal && !eventResult.getResultMap().containsKey("plan_thought")) {
                    eventResult.getResultMap().put("plan_thought", agentResponse.getPlanThought());
                }
                return ProjectedReplayEvent.builder()
                        .taskId(eventResult.getTaskId())
                        .taskOrder(eventResult.getTaskOrder().getAndIncrement())
                        .messageId(agentResponse.getMessageId())
                        .messageType("plan_thought")
                        .messageOrder(eventResult.getAndIncrOrder("plan_thought"))
                        .resultMap(payload)
                        .build();
            case "plan":
                if (eventResult.isInitPlan()) {
                    if (isFinal) {
                        eventResult.getResultMap().put("plan", agentResponse.getPlan());
                    }
                    return ProjectedReplayEvent.builder()
                            .taskId(eventResult.getTaskId())
                            .taskOrder(eventResult.getTaskOrder().getAndIncrement())
                            .messageId(agentResponse.getMessageId())
                            .messageType("plan")
                            .messageOrder(1)
                            .resultMap(buildPlanPayload(agentResponse, eventResult == null ? null : eventResult.getPlannerRoundId()))
                            .build();
                }
                return buildTaskEvent(eventResult, agentResponse, payload, isFinal);
            case "task":
                eventResult.renewTaskId();
                if (isFinal) {
                    List<Object> task = new ArrayList<>();
                    task.add(payload);
                    eventResult.setResultMapTask(task);
                }
                return ProjectedReplayEvent.builder()
                        .taskId(eventResult.getTaskId())
                        .taskOrder(eventResult.getTaskOrder().getAndIncrement())
                        .messageId(agentResponse.getMessageId())
                        .messageType("task")
                        .messageOrder(1)
                        .resultMap(payload)
                        .build();
            default:
                return buildTaskEvent(eventResult, agentResponse, payload, isFinal && !isFilterFinal);
        }
    }

    private ProjectedReplayEvent buildTaskEvent(EventResult eventResult,
                                                AgentResponse agentResponse,
                                                Map<String, Object> payload,
                                                boolean appendToState) {
        String taskId = eventResult.getTaskId();
        int messageOrder = 1;
        if (eventResult.getStreamTaskMessageType().contains(agentResponse.getMessageType())) {
            messageOrder = eventResult.getAndIncrOrder(taskId + ":" + agentResponse.getMessageType());
        }
        if (appendToState) {
            eventResult.setResultMapSubTask(payload);
        }
        return ProjectedReplayEvent.builder()
                .taskId(taskId)
                .taskOrder(eventResult.getTaskOrder().getAndIncrement())
                .messageId(agentResponse.getMessageId())
                .messageType("task")
                .messageOrder(messageOrder)
                .resultMap(payload)
                .build();
    }

    private Map<String, Object> buildPlanPayload(AgentResponse agentResponse, String fallbackPlannerRoundId) {
        if (agentResponse == null || agentResponse.getPlan() == null) {
            return Map.of();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", agentResponse.getPlan().getTitle());
        payload.put("stages", agentResponse.getPlan().getStages());
        payload.put("steps", agentResponse.getPlan().getSteps());
        payload.put("stepStatus", agentResponse.getPlan().getStepStatus());
        payload.put("notes", agentResponse.getPlan().getNotes());
        appendPlannerRoundId(payload, agentResponse == null ? null : agentResponse.getResultMap());
        appendPlannerRoundId(payload, fallbackPlannerRoundId);
        return payload;
    }

    private Map<String, Object> buildAgentResponsePayload(AgentResponse agentResponse) {
        if (agentResponse == null) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requestId", agentResponse.getRequestId());
        payload.put("messageId", agentResponse.getMessageId());
        payload.put("messageType", agentResponse.getMessageType());
        payload.put("messageTime", agentResponse.getMessageTime());
        payload.put("isFinal", Boolean.TRUE.equals(agentResponse.getIsFinal()));
        payload.put("finish", Boolean.TRUE.equals(agentResponse.getFinish()));
        if (StringUtils.isNotBlank(agentResponse.getDigitalEmployee())) {
            payload.put("digitalEmployee", agentResponse.getDigitalEmployee());
        }

        switch (agentResponse.getMessageType()) {
            case "tool_thought":
                payload.put("toolThought", agentResponse.getToolThought());
                break;
            case "task":
                payload.put("task", agentResponse.getTask());
                break;
            case "task_summary":
                payload.put("taskSummary", agentResponse.getTaskSummary());
                if (agentResponse.getResultMap() != null) {
                    payload.put("resultMap", new LinkedHashMap<>(agentResponse.getResultMap()));
                }
                break;
            case "plan_thought":
                payload.put("planThought", agentResponse.getPlanThought());
                break;
            case "plan":
                if (agentResponse.getPlan() != null) {
                    payload.put("title", agentResponse.getPlan().getTitle());
                    payload.put("stages", agentResponse.getPlan().getStages());
                    payload.put("steps", agentResponse.getPlan().getSteps());
                    payload.put("stepStatus", agentResponse.getPlan().getStepStatus());
                    payload.put("notes", agentResponse.getPlan().getNotes());
                }
                break;
            case "tool_result":
                payload.put("toolResult", agentResponse.getToolResult());
                break;
            case "tool_call":
            case "browser":
            case "code":
            case "html":
            case "markdown":
            case "ppt":
            case "file":
            case "knowledge":
            case "deep_search":
            case "data_analysis":
                if (agentResponse.getResultMap() != null) {
                    payload.put("resultMap", new LinkedHashMap<>(agentResponse.getResultMap()));
                }
                break;
            case "agent_stream":
                payload.put("result", agentResponse.getResult());
                break;
            case "result":
                payload.put("result", agentResponse.getResult());
                if (agentResponse.getResultMap() != null) {
                    if (agentResponse.getResultMap().containsKey("taskSummary")) {
                        payload.put("taskSummary", agentResponse.getResultMap().get("taskSummary"));
                    }
                    if (agentResponse.getResultMap().containsKey("fileList")) {
                        payload.put("fileList", agentResponse.getResultMap().get("fileList"));
                    }
                }
                break;
            default:
                if (agentResponse.getResultMap() != null) {
                    payload.put("resultMap", new LinkedHashMap<>(agentResponse.getResultMap()));
                }
                break;
        }
        appendPlannerRoundId(payload, agentResponse.getResultMap());
        return payload;
    }

    private void syncPlannerRoundId(EventResult eventResult, AgentResponse agentResponse) {
        if (eventResult == null || agentResponse == null || agentResponse.getResultMap() == null) {
            return;
        }
        Object plannerRoundId = agentResponse.getResultMap().get(EventResult.PLANNER_ROUND_ID_KEY);
        if (plannerRoundId == null) {
            return;
        }
        eventResult.setPlannerRoundId(String.valueOf(plannerRoundId));
    }

    private void appendPlannerRoundId(Map<String, Object> payload, Map<String, Object> resultMap) {
        if (payload == null || resultMap == null) {
            return;
        }
        Object plannerRoundId = resultMap.get(EventResult.PLANNER_ROUND_ID_KEY);
        if (plannerRoundId != null) {
            payload.put(EventResult.PLANNER_ROUND_ID_KEY, String.valueOf(plannerRoundId));
        }
    }

    private void appendPlannerRoundId(Map<String, Object> payload, String plannerRoundId) {
        if (payload == null || plannerRoundId == null || plannerRoundId.isBlank()) {
            return;
        }
        payload.putIfAbsent(EventResult.PLANNER_ROUND_ID_KEY, plannerRoundId);
    }
}
