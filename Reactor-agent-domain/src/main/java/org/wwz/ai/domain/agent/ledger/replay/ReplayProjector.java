package org.wwz.ai.domain.agent.ledger.replay;

import org.apache.commons.lang3.StringUtils;
import lombok.RequiredArgsConstructor;
import org.wwz.ai.domain.agent.ledger.model.ArtifactView;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunView;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.model.LlmInvocationView;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationView;
import org.wwz.ai.domain.agent.reactor.model.constant.Constants;
import org.wwz.ai.domain.agent.reactor.model.multi.EventResult;
import org.wwz.ai.domain.agent.reactor.model.response.GptProcessResult;
import org.wwz.ai.domain.agent.ledger.model.replay.ProjectedReplayEvent;
import org.wwz.ai.domain.agent.ledger.model.replay.ReplayFactBundle;
import org.wwz.ai.domain.agent.ledger.replay.projector.ToolInvocationProjectorRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 历史回放共享投影入口。
 * 这里只负责遍历顺序与 artifact 归组，具体工具解析全部委托给 registry。
 */
@RequiredArgsConstructor
public class ReplayProjector {

    private final ToolInvocationProjectorRegistry toolInvocationProjectorRegistry;

    public List<ProjectedReplayEvent> projectHistory(ReplayFactBundle bundle) {
        EventResult state = new EventResult();
        List<ProjectedReplayEvent> events = new ArrayList<>();
        if (bundle == null) {
            return events;
        }

        boolean hasLlm = bundle.getLlmInvocations() != null && !bundle.getLlmInvocations().isEmpty();
        boolean hasTool = bundle.getToolInvocations() != null && !bundle.getToolInvocations().isEmpty();

        if (hasLlm && hasTool) {
            events.addAll(projectMixedHistory(bundle, state));
            appendRunSummaryFallback(events, bundle, state);
            return events;
        }

        if (hasLlm) {
            events.addAll(projectLlmHistory(bundle, state));
        }

        if (hasTool) {
            events.addAll(projectToolHistory(bundle, state));
        }

        if (!hasLlm && !hasTool) {
            appendRunSummaryFallback(events, bundle, state);
            return events;
        }
        appendRunSummaryFallback(events, bundle, state);
        return events;
    }

    public List<GptProcessResult> projectHistoryFrames(ReplayFactBundle bundle) {
        List<ProjectedReplayEvent> events = projectHistory(bundle);
        if (events.isEmpty()) {
            return List.of();
        }
        List<GptProcessResult> frames = new ArrayList<>(events.size());
        String requestId = bundle == null || bundle.getRun() == null ? null : bundle.getRun().getRequestId();
        for (ProjectedReplayEvent event : events) {
            frames.add(toFrame(requestId, event, true, Constants.SUCCESS));
        }
        return frames;
    }

    /**
     * 实时与历史共用同一套 frame 组装逻辑。
     * 实时链路只需要提供已经收口好的 ProjectedReplayEvent，即可得到前端可直接消费的 eventData。
     */
    public GptProcessResult projectFrame(String requestId,
                                         ProjectedReplayEvent event,
                                         boolean finished,
                                         String status) {
        return toFrame(requestId, event, finished, status);
    }

    private List<ProjectedReplayEvent> projectLlmHistory(ReplayFactBundle bundle, EventResult state) {
        if (bundle == null || bundle.getLlmInvocations() == null || bundle.getLlmInvocations().isEmpty()) {
            return List.of();
        }
        List<ProjectedReplayEvent> events = new ArrayList<>();
        for (LlmInvocationView invocation : bundle.getLlmInvocations()) {
            if (shouldSkipLlmReplay(invocation) || StringUtils.isBlank(invocation.getResponseText())) {
                continue;
            }
            String messageType = resolveLlmMessageType(invocation);
            events.add(buildLlmReplayEvent(bundle, state, invocation, messageType, null));
        }
        return events;
    }

    private List<ProjectedReplayEvent> projectMixedHistory(ReplayFactBundle bundle, EventResult state) {
        List<ProjectedReplayEvent> events = new ArrayList<>();
        Map<Long, List<ArtifactView>> artifactsByInvocationId = groupArtifacts(bundle.getArtifacts());
        Map<Long, List<ToolInvocationView>> toolsByLlmInvocationId = groupToolsByLlmInvocationId(bundle.getToolInvocations());
        List<ToolInvocationView> orphanTools = new ArrayList<>();

        if (bundle.getToolInvocations() != null) {
            for (ToolInvocationView invocation : bundle.getToolInvocations()) {
                if (invocation == null) {
                    continue;
                }
                if (invocation.getLlmInvocationId() == null) {
                    orphanTools.add(invocation);
                }
            }
        }

        List<LlmInvocationView> llmInvocations = sortLlmInvocations(bundle.getLlmInvocations());
        for (LlmInvocationView llmInvocation : llmInvocations) {
            if (shouldSkipLlmReplay(llmInvocation)) {
                continue;
            }

            List<ToolInvocationView> linkedTools = toolsByLlmInvocationId.get(llmInvocation.getId());
            String messageType = null;
            if (StringUtils.isNotBlank(llmInvocation.getResponseText())) {
                messageType = resolveLlmMessageType(llmInvocation);
                events.add(buildLlmReplayEvent(
                        bundle,
                        state,
                        llmInvocation,
                        messageType,
                        resolvePlannerRoundId(messageType, linkedTools)
                ));
            }

            if (linkedTools == null || linkedTools.isEmpty()) {
                continue;
            }

            boolean reuseCurrentTaskGroup = "tool_thought".equals(messageType);
            for (ToolInvocationView toolInvocation : linkedTools) {
                List<ArtifactView> artifacts = artifactsByInvocationId.getOrDefault(toolInvocation.getId(), List.of());
                events.addAll(toolInvocationProjectorRegistry.project(
                        toolInvocation,
                        artifacts,
                        state,
                        reuseCurrentTaskGroup
                ));
            }
        }

        for (ToolInvocationView orphanTool : sortToolInvocations(orphanTools)) {
            List<ArtifactView> artifacts = artifactsByInvocationId.getOrDefault(orphanTool.getId(), List.of());
            events.addAll(toolInvocationProjectorRegistry.project(orphanTool, artifacts, state));
        }
        return events;
    }

    /**
     * digital employee 生成属于内部配置 ask，不应投影成前端可见 thought。
     * 否则会污染历史重放，并导致 PlanSolve plannerRounds 平白增加一版。
     */
    private boolean shouldSkipLlmReplay(LlmInvocationView invocation) {
        return invocation == null
                || ExecutionLedgerConstants.CALL_KIND_INTERNAL_DIGITAL_EMPLOYEE.equals(invocation.getCallKind());
    }

    private ProjectedReplayEvent buildLlmReplayEvent(ReplayFactBundle bundle,
                                                     EventResult state,
                                                     LlmInvocationView invocation,
                                                     String messageType,
                                                     String plannerRoundId) {
        syncPlannerRoundState(state, messageType, plannerRoundId);
        List<Map<String, Object>> artifactRefs = null;
        if ("result".equals(messageType)) {
            SummaryReplayResultResolver.ResolvedSummary resolvedSummary =
                    SummaryReplayResultResolver.resolve(invocation.getResponseText(), bundle == null ? null : bundle.getArtifacts());
            artifactRefs = resolvedSummary.getArtifactRefs().isEmpty() ? null : resolvedSummary.getArtifactRefs();
        }
        return ProjectedReplayEvent.builder()
                .taskId(state.getTaskId())
                .taskOrder(state.getTaskOrder().getAndIncrement())
                .messageId(resolveLlmMessageId(invocation))
                .messageType(resolveOuterMessageType(messageType))
                .messageOrder(state.getAndIncrOrder(state.getTaskId() + ":" + messageType))
                .resultMap(buildLlmResponse(bundle, invocation, messageType, plannerRoundId))
                .artifactRefs(artifactRefs)
                .build();
    }

    private List<ProjectedReplayEvent> projectToolHistory(ReplayFactBundle bundle, EventResult state) {
        if (bundle == null || bundle.getToolInvocations() == null || bundle.getToolInvocations().isEmpty()) {
            return List.of();
        }
        List<ProjectedReplayEvent> events = new ArrayList<>();
        Map<Long, List<ArtifactView>> artifactsByInvocationId = groupArtifacts(bundle.getArtifacts());
        for (ToolInvocationView invocation : sortToolInvocations(bundle.getToolInvocations())) {
            if (invocation == null) {
                continue;
            }
            List<ArtifactView> artifacts = artifactsByInvocationId.getOrDefault(invocation.getId(), List.of());
            events.addAll(toolInvocationProjectorRegistry.project(invocation, artifacts, state));
        }
        return events;
    }

    private String resolveLlmMessageType(LlmInvocationView invocation) {
        String agentName = invocation == null ? null : invocation.getAgentName();
        // 目前历史语义只允许在 projector 一处通过 agent_name 推断，
        // 这样 realtime / history 才能共用同一套 messageType 约定。
        if ("planning".equals(agentName)) {
            return "plan_thought";
        }
        if ("summary".equals(agentName)) {
            return "result";
        }
        if ("executor".equals(agentName)
                && invocation != null
                && Integer.valueOf(0).equals(invocation.getToolCallCount())) {
            return "task_summary";
        }
        return "tool_thought";
    }

    /**
     * 当历史账本里没有显式结果事件时，补一个最终结论事件，避免前端恢复后缺少底部结论区。
     */
    private void appendRunSummaryFallback(List<ProjectedReplayEvent> events, ReplayFactBundle bundle, EventResult state) {
        if (bundle == null || bundle.getRun() == null || hasResultEvent(events)) {
            return;
        }
        DialogueRunView run = bundle.getRun();
        if (StringUtils.isBlank(run.getFinalSummaryText())) {
            return;
        }

        Map<String, Object> resultMap = new LinkedHashMap<>();
        SummaryReplayResultResolver.ResolvedSummary resolvedSummary =
                SummaryReplayResultResolver.resolve(run.getFinalSummaryText(), bundle.getArtifacts());
        resultMap.put("requestId", run.getRequestId());
        resultMap.put("messageId", run.getRequestId() + ":summary");
        resultMap.put("messageTime", resolveRunMessageTime(run));
        resultMap.put("messageType", "result");
        resultMap.put("isFinal", true);
        resultMap.put("finish", run.getStatus() != null && run.getStatus() != ExecutionLedgerConstants.STATUS_RUNNING);
        resultMap.put("result", resolvedSummary.getSummaryText());
        resultMap.put("taskSummary", resolvedSummary.getSummaryText());
        if (!resolvedSummary.getFileList().isEmpty()) {
            resultMap.put("fileList", resolvedSummary.getFileList());
        }

        String taskId = state.getTaskId();
        events.add(ProjectedReplayEvent.builder()
                .taskId(taskId)
                .taskOrder(state.getTaskOrder().getAndIncrement())
                .messageId(run.getRequestId() + ":summary")
                .messageType("task")
                .messageOrder(state.getAndIncrOrder(taskId + ":result"))
                .resultMap(resultMap)
                .artifactRefs(resolvedSummary.getArtifactRefs().isEmpty() ? null : resolvedSummary.getArtifactRefs())
                .build());
    }

    private boolean hasResultEvent(List<ProjectedReplayEvent> events) {
        if (events == null || events.isEmpty()) {
            return false;
        }
        for (ProjectedReplayEvent event : events) {
            if (!(event.getResultMap() instanceof Map<?, ?> resultMap)) {
                continue;
            }
            Object messageType = resultMap.get("messageType");
            if ("result".equals(messageType) || "task_summary".equals(messageType)) {
                return true;
            }
        }
        return false;
    }

    private String resolveRunMessageTime(DialogueRunView run) {
        if (run == null) {
            return String.valueOf(System.currentTimeMillis());
        }
        if (run.getFinishedAt() != null) {
            return String.valueOf(run.getFinishedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
        }
        if (run.getStartedAt() != null) {
            return String.valueOf(run.getStartedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
        }
        return String.valueOf(System.currentTimeMillis());
    }

    private String resolveLlmMessageId(LlmInvocationView invocation) {
        return StringUtils.defaultIfBlank(invocation.getAgentName(), "llm")
                + ":"
                + String.valueOf(invocation.getInvocationSeq());
    }

    private Object buildLlmResponse(ReplayFactBundle bundle,
                                    LlmInvocationView invocation,
                                    String messageType,
                                    String plannerRoundId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("requestId", bundle == null || bundle.getRun() == null ? null : bundle.getRun().getRequestId());
        response.put("messageId", resolveLlmMessageId(invocation));
        response.put("messageType", messageType);
        response.put("messageTime", invocation.getFinishedAt() == null
                ? String.valueOf(System.currentTimeMillis())
                : String.valueOf(invocation.getFinishedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()));
        response.put("isFinal", true);
        response.put("finish", "result".equals(messageType));
        if ("plan_thought".equals(messageType)) {
            response.put("planThought", invocation.getResponseText());
            appendPlannerRoundId(response, plannerRoundId);
        } else if ("tool_thought".equals(messageType)) {
            response.put("toolThought", invocation.getResponseText());
        } else if ("task_summary".equals(messageType)) {
            response.put("taskSummary", invocation.getResponseText());
            response.put("resultMap", new LinkedHashMap<>());
        } else if ("result".equals(messageType)) {
            SummaryReplayResultResolver.ResolvedSummary resolvedSummary =
                    SummaryReplayResultResolver.resolve(invocation.getResponseText(), bundle == null ? null : bundle.getArtifacts());
            response.put("result", resolvedSummary.getSummaryText());
            response.put("taskSummary", resolvedSummary.getSummaryText());
            if (!resolvedSummary.getFileList().isEmpty()) {
                response.put("fileList", resolvedSummary.getFileList());
            }
        } else {
            response.put("result", invocation.getResponseText());
            response.put("taskSummary", invocation.getResponseText());
        }
        return response;
    }

    private void syncPlannerRoundState(EventResult state, String messageType, String plannerRoundId) {
        if (state == null) {
            return;
        }
        if ("plan_thought".equals(messageType)) {
            state.setPlannerRoundId(plannerRoundId);
        }
    }

    private void appendPlannerRoundId(Map<String, Object> response, String plannerRoundId) {
        if (response == null || StringUtils.isBlank(plannerRoundId)) {
            return;
        }
        response.put(EventResult.PLANNER_ROUND_ID_KEY, plannerRoundId);
    }

    private String resolvePlannerRoundId(String messageType, List<ToolInvocationView> linkedTools) {
        if (!"plan_thought".equals(messageType) || linkedTools == null || linkedTools.isEmpty()) {
            return null;
        }
        for (ToolInvocationView linkedTool : linkedTools) {
            if (linkedTool != null
                    && "planning".equals(linkedTool.getToolName())
                    && linkedTool.getId() != null) {
                return String.valueOf(linkedTool.getId());
            }
        }
        return null;
    }

    private GptProcessResult toFrame(String requestId,
                                     ProjectedReplayEvent event,
                                     boolean finished,
                                     String status) {
        Map<String, Object> resultMap = new LinkedHashMap<>();
        // 历史 frame 顶层统一打 history 标识；实时链路只复用 eventData，
        // 不直接透传这个顶层值，避免覆盖 SSE 原有 agentType。
        resultMap.put("agentType", "history");
        resultMap.put("multiAgent", new LinkedHashMap<>());
        Map<String, Object> eventData = new LinkedHashMap<>();
        eventData.put("taskId", event.getTaskId());
        eventData.put("taskOrder", event.getTaskOrder());
        eventData.put("messageType", event.getMessageType());
        eventData.put("messageOrder", event.getMessageOrder());
        eventData.put("messageId", event.getMessageId());
        if (event.getArtifactRefs() != null) {
            eventData.put("artifactRefs", event.getArtifactRefs());
        }
        eventData.put("resultMap", event.getResultMap());
        resultMap.put("eventData", eventData);
        return GptProcessResult.builder()
                .status(status)
                .finished(finished)
                .reqId(requestId)
                .resultMap(resultMap)
                .build();
    }

    private String resolveOuterMessageType(String logicalMessageType) {
        if ("plan_thought".equals(logicalMessageType)) {
            return "plan_thought";
        }
        return "task";
    }

    private Map<Long, List<ArtifactView>> groupArtifacts(List<ArtifactView> artifacts) {
        Map<Long, List<ArtifactView>> result = new LinkedHashMap<>();
        if (artifacts == null) {
            return result;
        }
        for (ArtifactView artifact : artifacts) {
            if (artifact == null || artifact.getToolInvocationId() == null) {
                continue;
            }
            result.computeIfAbsent(artifact.getToolInvocationId(), key -> new ArrayList<>()).add(artifact);
        }
        return result;
    }

    private Map<Long, List<ToolInvocationView>> groupToolsByLlmInvocationId(List<ToolInvocationView> toolInvocations) {
        Map<Long, List<ToolInvocationView>> result = new LinkedHashMap<>();
        if (toolInvocations == null) {
            return result;
        }
        for (ToolInvocationView invocation : sortToolInvocations(toolInvocations)) {
            if (invocation == null || invocation.getLlmInvocationId() == null) {
                continue;
            }
            result.computeIfAbsent(invocation.getLlmInvocationId(), key -> new ArrayList<>()).add(invocation);
        }
        return result;
    }

    private List<LlmInvocationView> sortLlmInvocations(List<LlmInvocationView> llmInvocations) {
        if (llmInvocations == null || llmInvocations.isEmpty()) {
            return List.of();
        }
        List<LlmInvocationView> result = new ArrayList<>(llmInvocations);
        result.sort(Comparator
                .comparing(LlmInvocationView::getInvocationSeq, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(LlmInvocationView::getId, Comparator.nullsLast(Long::compareTo)));
        return result;
    }

    private List<ToolInvocationView> sortToolInvocations(List<ToolInvocationView> toolInvocations) {
        if (toolInvocations == null || toolInvocations.isEmpty()) {
            return List.of();
        }
        List<ToolInvocationView> result = new ArrayList<>(toolInvocations);
        result.sort(Comparator
                .comparing(ToolInvocationView::getDispatchIndex, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(ToolInvocationView::getStartedAt, Comparator.nullsLast(java.time.LocalDateTime::compareTo))
                .thenComparing(ToolInvocationView::getId, Comparator.nullsLast(Long::compareTo)));
        return result;
    }
}
