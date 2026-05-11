package org.wwz.ai.domain.agent.runtime.tool.common.planning;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.dto.Plan;

import java.util.ArrayList;
import java.util.List;

/**
 * 普通 replan 生命周期服务。
 * 统一收口 create/update/mark_step/finish 的状态修复和自动推进逻辑。
 */
public class PlanLifecycleService {

    private static final String STATUS_NOT_STARTED = "not_started";
    private static final String STATUS_IN_PROGRESS = "in_progress";
    private static final String STATUS_COMPLETED = "completed";
    private static final String STATUS_BLOCKED = "blocked";

    /**
     * 创建计划，并自动激活首个可执行步骤。
     */
    public PlanLifecycleResult create(String title, List<String> steps) {
        validateNonEmptySteps(steps);
        Plan plan = Plan.create(title, copySteps(steps));
        activateFirstNotStarted(plan);
        return buildResult(plan, true, false);
    }

    /**
     * 更新剩余计划，冻结已完成前缀，仅替换未完成部分。
     */
    public PlanLifecycleResult update(Plan plan, String title, List<String> remainingSteps) {
        validatePlanExists(plan);
        normalizePlan(plan);

        if (StringUtils.isNotBlank(title)) {
            plan.setTitle(title);
        }
        if (remainingSteps == null) {
            return ensureExecutable(plan);
        }
        validateNonEmptySteps(remainingSteps);

        int completedPrefixSize = countCompletedPrefix(plan);
        List<String> mergedSteps = new ArrayList<>();
        List<String> mergedStatus = new ArrayList<>();
        List<String> mergedNotes = new ArrayList<>();

        for (int index = 0; index < completedPrefixSize; index++) {
            mergedSteps.add(plan.getSteps().get(index));
            mergedStatus.add(STATUS_COMPLETED);
            mergedNotes.add(plan.getNotes().get(index));
        }
        for (String step : remainingSteps) {
            mergedSteps.add(step);
            mergedStatus.add(STATUS_NOT_STARTED);
            mergedNotes.add("");
        }

        plan.setSteps(mergedSteps);
        plan.setStepStatus(mergedStatus);
        plan.setNotes(mergedNotes);

        return ensureExecutable(plan);
    }

    /**
     * 标记步骤状态，并在完成时自动推进或自动结束。
     */
    public PlanLifecycleResult markStep(Plan plan, Integer stepIndex, String status, String note) {
        validatePlanExists(plan);
        normalizePlan(plan);
        validateStepIndex(plan, stepIndex);
        validateStepStatus(status);

        String currentStatus = plan.getStepStatus().get(stepIndex);
        if (STATUS_COMPLETED.equals(currentStatus) && !StringUtils.equals(status, STATUS_COMPLETED)) {
            throw new IllegalStateException("completed step is frozen and cannot be mutated");
        }

        Integer currentStepIndex = plan.getCurrentStepIndex();
        if (STATUS_COMPLETED.equals(status)
                && currentStepIndex != null
                && !currentStepIndex.equals(stepIndex)) {
            throw new IllegalStateException("only current step can be completed in ordinary replan mode");
        }

        plan.updateStepStatus(stepIndex, status, note);

        if (!STATUS_COMPLETED.equals(status)) {
            return ensureExecutable(plan);
        }

        if (isAllStepsCompleted(plan)) {
            return buildResult(plan, false, true);
        }

        boolean autoAdvanced = activateFirstNotStarted(plan);
        return buildResult(plan, autoAdvanced, false);
    }

    /**
     * 显式提前结束剩余计划。
     */
    public PlanLifecycleResult finish(Plan plan) {
        if (plan == null) {
            plan = Plan.empty();
        }
        normalizePlan(plan);
        for (int index = 0; index < plan.getSteps().size(); index++) {
            plan.updateStepStatus(index, STATUS_COMPLETED, plan.getNotes().get(index));
        }
        return buildResult(plan, false, true);
    }

    /**
     * 计划未完成但缺失当前步骤时，尝试执行受控修复。
     */
    public PlanLifecycleResult ensureExecutable(Plan plan) {
        validatePlanExists(plan);
        normalizePlan(plan);

        if (isAllStepsCompleted(plan)) {
            return buildResult(plan, false, true);
        }
        if (plan.getCurrentStepIndex() != null) {
            return buildResult(plan, false, false);
        }

        boolean repaired = activateFirstNotStarted(plan);
        if (!repaired) {
            throw new IllegalStateException("current step is missing and cannot be repaired");
        }
        return buildResult(plan, true, false);
    }

    /**
     * 判断计划是否已全部完成。
     */
    public boolean isAllStepsCompleted(Plan plan) {
        if (plan == null || plan.getSteps() == null || plan.getSteps().isEmpty()) {
            return true;
        }
        normalizePlan(plan);
        return plan.getStepStatus().stream().allMatch(STATUS_COMPLETED::equals);
    }

    private void validatePlanExists(Plan plan) {
        if (plan == null) {
            throw new IllegalStateException("No plan exists. Create a plan first.");
        }
    }

    private void validateNonEmptySteps(List<String> steps) {
        if (steps == null || steps.isEmpty()) {
            throw new IllegalArgumentException("plan steps cannot be empty");
        }
        for (String step : steps) {
            if (StringUtils.isBlank(step)) {
                throw new IllegalArgumentException("plan step cannot be blank");
            }
        }
    }

    private void validateStepIndex(Plan plan, Integer stepIndex) {
        if (stepIndex == null) {
            throw new IllegalArgumentException("step_index is required for mark_step command");
        }
        if (stepIndex < 0 || stepIndex >= plan.getSteps().size()) {
            throw new IllegalArgumentException("Invalid step index: " + stepIndex);
        }
    }

    private void validateStepStatus(String status) {
        if (!STATUS_NOT_STARTED.equals(status)
                && !STATUS_IN_PROGRESS.equals(status)
                && !STATUS_COMPLETED.equals(status)
                && !STATUS_BLOCKED.equals(status)) {
            throw new IllegalArgumentException("Invalid step status: " + status);
        }
    }

    /**
     * 修正历史/手工构造计划的列表长度，避免出现空指针和错位。
     */
    private void normalizePlan(Plan plan) {
        List<String> steps = plan.getSteps() == null ? new ArrayList<>() : new ArrayList<>(plan.getSteps());
        List<String> status = plan.getStepStatus() == null ? new ArrayList<>() : new ArrayList<>(plan.getStepStatus());
        List<String> notes = plan.getNotes() == null ? new ArrayList<>() : new ArrayList<>(plan.getNotes());

        while (status.size() < steps.size()) {
            status.add(STATUS_NOT_STARTED);
        }
        while (notes.size() < steps.size()) {
            notes.add("");
        }
        if (status.size() > steps.size()) {
            status = new ArrayList<>(status.subList(0, steps.size()));
        }
        if (notes.size() > steps.size()) {
            notes = new ArrayList<>(notes.subList(0, steps.size()));
        }

        plan.setSteps(steps);
        plan.setStepStatus(status);
        plan.setNotes(notes);
    }

    /**
     * 激活第一条未开始步骤，并清理异常残留的 in_progress。
     */
    private boolean activateFirstNotStarted(Plan plan) {
        Integer nextIndex = null;
        for (int index = 0; index < plan.getStepStatus().size(); index++) {
            String status = plan.getStepStatus().get(index);
            if (STATUS_NOT_STARTED.equals(status)) {
                nextIndex = index;
                break;
            }
        }
        if (nextIndex == null) {
            return false;
        }
        for (int index = 0; index < plan.getStepStatus().size(); index++) {
            if (STATUS_IN_PROGRESS.equals(plan.getStepStatus().get(index))) {
                plan.getStepStatus().set(index, STATUS_NOT_STARTED);
            }
        }
        plan.getStepStatus().set(nextIndex, STATUS_IN_PROGRESS);
        return true;
    }

    private int countCompletedPrefix(Plan plan) {
        int count = 0;
        for (String status : plan.getStepStatus()) {
            if (!STATUS_COMPLETED.equals(status)) {
                break;
            }
            count++;
        }
        return count;
    }

    private List<String> copySteps(List<String> steps) {
        return steps == null ? List.of() : new ArrayList<>(steps);
    }

    private PlanLifecycleResult buildResult(Plan plan, boolean autoAdvanced, boolean autoFinished) {
        return PlanLifecycleResult.builder()
                .plan(plan)
                .currentStep(plan == null ? "" : plan.getCurrentStep())
                .currentStepIndex(plan == null ? null : plan.getCurrentStepIndex())
                .autoAdvanced(autoAdvanced)
                .autoFinished(autoFinished)
                .build();
    }
}
