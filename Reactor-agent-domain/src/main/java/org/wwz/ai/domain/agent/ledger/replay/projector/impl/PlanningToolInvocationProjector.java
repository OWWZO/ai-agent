package org.wwz.ai.domain.agent.ledger.replay.projector.impl;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.dto.Plan;
import org.wwz.ai.domain.agent.ledger.model.ArtifactView;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationView;
import org.wwz.ai.domain.agent.reactor.model.multi.EventResult;
import org.wwz.ai.domain.agent.ledger.model.replay.ProjectedReplayEvent;
import org.wwz.ai.domain.agent.reactor.model.response.AgentResponse;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.PlanningToolOutput;
import org.wwz.ai.domain.agent.runtime.tool.common.planning.PlanLifecycleService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * planning 工具历史回放投影。
 * 直接根据工具入参重建 plan / task 事件，避免历史里退化成普通 tool_result。
 */
/**
 * 规划工具的历史回放投影器，负责按规划步骤组织任务事件。
 */
public class PlanningToolInvocationProjector extends AbstractToolInvocationProjector {

    private static final String PLAN_STATE_KEY = "__history_planning_tool_plan";
    private final PlanLifecycleService lifecycleService = new PlanLifecycleService();

    @Override
    public boolean supports(String toolName) {
        return "planning".equals(toolName);
    }

    @Override
    public List<ProjectedReplayEvent> project(ToolInvocationView invocation,
                                              List<ArtifactView> artifacts,
                                              EventResult state) {
        String plannerRoundId = resolvePlannerRoundId(invocation);
        state.setPlannerRoundId(plannerRoundId);
        Plan plan = resolveProjectedPlan(state, invocation);
        if (plan == null) {
            return List.of();
        }
        rememberPlan(state, plan);

        // 一个 planning invocation 先投影 plan，再按当前步骤拆成 task；task 使用新的 taskId，避免覆盖 plan 事件。
        List<ProjectedReplayEvent> events = new ArrayList<>();
        events.add(ProjectedReplayEvent.builder()
                .taskId(state.getTaskId())
                .taskOrder(state.getTaskOrder().getAndIncrement())
                .messageId(resolveMessageId(invocation, "plan"))
                .messageType("plan")
                .messageOrder(1)
                .resultMap(buildPlanPayload(plan, plannerRoundId))
                .build());

        if (!hasCurrentStep(plan)) {
            return events;
        }

        String[] currentSteps = plan.getCurrentStep().split("<sep>");
        // currentStep 可能包含并行/组合步骤，逐段生成任务事件并沿用同一 plannerRoundId 方便前端归组。
        for (String rawStep : currentSteps) {
            if (StringUtils.isBlank(rawStep)) {
                continue;
            }
            state.renewTaskId();
            events.add(ProjectedReplayEvent.builder()
                    .taskId(state.getTaskId())
                    .taskOrder(state.getTaskOrder().getAndIncrement())
                    .messageId(resolveMessageId(invocation, "task"))
                    .messageType("task")
                    .messageOrder(1)
                    .resultMap(buildTaskPayload(invocation, rawStep, plannerRoundId))
                    .artifactRefs(CollectionUtils.isEmpty(artifacts) ? null : buildArtifactRefs(artifacts))
                    .build());
        }
        return events;
    }

    @SuppressWarnings("unchecked")
    private Plan resolvePlan(EventResult state) {
        if (state == null || state.getResultMap() == null) {
            return null;
        }
        Object stored = state.getResultMap().get(PLAN_STATE_KEY);
        return stored instanceof Plan ? (Plan) stored : null;
    }

    private void rememberPlan(EventResult state, Plan plan) {
        if (state == null || plan == null) {
            return;
        }
        state.getResultMap().put(PLAN_STATE_KEY, plan);
    }

    private Plan resolveProjectedPlan(EventResult state, ToolInvocationView invocation) {
        if (invocation != null && invocation.getStructuredOutput() instanceof PlanningToolOutput output) {
            // 新账本优先使用持久化 afterPlan，避免回放时重新执行生命周期逻辑产生不同结果。
            Plan afterPlan = output.getAfterPlan() == null ? null : output.getAfterPlan().copy();
            if (afterPlan == null) {
                return null;
            }
            if (!allStepsCompleted(afterPlan)) {
                lifecycleService.ensureExecutable(afterPlan);
            }
            return afterPlan;
        }
        Map<String, Object> input = readMap(invocation == null ? null : invocation.getInputJson());
        // 旧账本没有结构化输出时，基于 state 中的上一版计划应用命令，尽力恢复连续状态。
        Plan plan = applyCommandSafely(resolvePlan(state), input);
        if (plan != null && !hasCurrentStep(plan) && !allStepsCompleted(plan)) {
            try {
                lifecycleService.ensureExecutable(plan);
            } catch (IllegalArgumentException | IllegalStateException ignore) {
                // 兼容旧账本：fallback 回放尽最大努力恢复，不因异常中断整段历史。
            }
        }
        return plan;
    }

    @SuppressWarnings("unchecked")
    private Plan applyCommand(Plan currentPlan, Map<String, Object> input) {
        String command = String.valueOf(input.getOrDefault("command", ""));
        // projector 只重放 planning 的状态命令，不调用外部工具；无前置 plan 的 update/mark/finish 保持不可投影。
        switch (command) {
            case "create":
                return Plan.create(
                        String.valueOf(input.getOrDefault("title", "")),
                        toStringList(input.get("steps"))
                );
            case "update":
                if (currentPlan == null) {
                    return null;
                }
                return lifecycleService.update(
                        currentPlan,
                        input.containsKey("title") ? String.valueOf(input.get("title")) : null,
                        input.containsKey("steps") ? toStringList(input.get("steps")) : null
                ).getPlan();
            case "mark_step":
                if (currentPlan == null) {
                    return null;
                }
                Integer stepIndex = toInteger(input.get("step_index"));
                if (stepIndex == null) {
                    return currentPlan;
                }
                return lifecycleService.markStep(
                        currentPlan,
                        stepIndex,
                        input.containsKey("step_status") ? String.valueOf(input.get("step_status")) : null,
                        input.containsKey("step_notes") ? String.valueOf(input.get("step_notes")) : null
                ).getPlan();
            case "finish":
                return lifecycleService.finish(currentPlan).getPlan();
            default:
                return currentPlan;
        }
    }

    private Plan applyCommandSafely(Plan currentPlan, Map<String, Object> input) {
        try {
            return applyCommand(currentPlan, input);
        } catch (IllegalArgumentException | IllegalStateException ignore) {
            // 兼容旧 planning 历史：单条非法命令只放弃本次迁移，不中断同一会话后续事件回放。
            return currentPlan;
        }
    }

    private Map<String, Object> buildPlanPayload(Plan plan, String plannerRoundId) {
        AgentResponse.Plan formattedPlan = AgentResponse.formatSteps(AgentResponse.Plan.builder()
                .title(plan.getTitle())
                .steps(copyList(plan.getSteps()))
                .stepStatus(copyList(plan.getStepStatus()))
                .notes(copyList(plan.getNotes()))
                .build());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", formattedPlan.getTitle());
        payload.put("stages", formattedPlan.getStages());
        payload.put("steps", formattedPlan.getSteps());
        payload.put("stepStatus", formattedPlan.getStepStatus());
        payload.put("notes", formattedPlan.getNotes());
        if (StringUtils.isNotBlank(plannerRoundId)) {
            payload.put(EventResult.PLANNER_ROUND_ID_KEY, plannerRoundId);
        }
        return payload;
    }

    private Map<String, Object> buildTaskPayload(ToolInvocationView invocation, String rawStep, String plannerRoundId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requestId", invocation == null ? null : invocation.getRequestId());
        payload.put("messageId", resolveMessageId(invocation, "task"));
        payload.put("messageTime", resolveMessageTime(invocation));
        payload.put("messageType", "task");
        payload.put("isFinal", true);
        payload.put("finish", false);
        payload.put("task", rawStep.replaceAll("^执行顺序(\\d+)\\.\\s?", ""));
        if (StringUtils.isNotBlank(plannerRoundId)) {
            payload.put(EventResult.PLANNER_ROUND_ID_KEY, plannerRoundId);
        }
        return payload;
    }

    private String resolvePlannerRoundId(ToolInvocationView invocation) {
        return invocation == null || invocation.getId() == null ? null : String.valueOf(invocation.getId());
    }

    private boolean hasCurrentStep(Plan plan) {
        return plan != null && StringUtils.isNotBlank(plan.getCurrentStep());
    }

    private boolean allStepsCompleted(Plan plan) {
        if (plan == null || CollectionUtils.isEmpty(plan.getStepStatus())) {
            return true;
        }
        return plan.getStepStatus().stream().allMatch("completed"::equals);
    }

    private List<String> toStringList(Object value) {
        if (!(value instanceof List<?> items)) {
            return List.of();
        }
        List<String> result = new ArrayList<>(items.size());
        for (Object item : items) {
            result.add(String.valueOf(item));
        }
        return result;
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignore) {
            return null;
        }
    }

    private List<String> copyList(List<String> source) {
        return source == null ? List.of() : new ArrayList<>(source);
    }
}
