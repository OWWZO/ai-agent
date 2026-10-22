package org.wwz.ai.domain.agent.runtime.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 计划类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Plan {

    /**
     * 计划标题
     */
    private String title;

    /**
     * 计划步骤列表
     */
    private List<String> steps;

    /**
     * 步骤状态列表
     */
    private List<String> stepStatus;

    /**
     * 步骤备注列表
     */
    private List<String> notes;

    /**
     * 创建空计划，便于 finish 等幂等场景复用。
     */
    public static Plan empty() {
        return Plan.builder()
                .title("")
                .steps(new ArrayList<>())
                .stepStatus(new ArrayList<>())
                .notes(new ArrayList<>())
                .build();
    }

    /**
     * 创建新计划
     */
    public static Plan create(String title, List<String> steps) {
        List<String> status = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        for (int i = 0; i < steps.size(); i++) {
            status.add("not_started");
            notes.add("");
        }

        return Plan.builder()
                .title(title)
                .steps(steps)
                .stepStatus(status)
                .notes(notes)
                .build();
    }

    /**
     * 深拷贝计划快照，避免持久化与 replay 共用同一份可变引用。
     */
    public Plan copy() {
        return Plan.builder()
                .title(title)
                .steps(copyList(steps))
                .stepStatus(copyList(stepStatus))
                .notes(copyList(notes))
                .build();
    }

    /**
     * 更新计划
     */
    public void update(String title, List<String> newSteps) {
        if (title != null) {
            this.title = title;
        }

        if (newSteps != null) {
            List<String> newStatuses = new ArrayList<>();
            List<String> newNotes = new ArrayList<>();

            for (int i = 0; i < newSteps.size(); i++) {
                if (i < this.steps.size() && newSteps.get(i).equals(this.steps.get(i))) {
                    // 保持原有状态和备注
                    newStatuses.add(this.stepStatus.get(i));
                    newNotes.add(this.notes.get(i));
                } else {
                    // 新步骤使用默认状态和空备注
                    newStatuses.add("not_started");
                    newNotes.add("");
                }
            }

            this.steps = newSteps;
            this.stepStatus = newStatuses;
            this.notes = newNotes;
        }
    }

    /**
     * 更新步骤状态
     */
    public void updateStepStatus(int stepIndex, String status, String note) {
        if (stepIndex < 0 || stepIndex >= steps.size()) {
            throw new IllegalArgumentException("Invalid step index: " + stepIndex);
        }

        if (status != null) {
            this.stepStatus.set(stepIndex, status);
        }

        if (note != null) {
            this.notes.set(stepIndex, note);
        }
    }

    /**
     * 更新步骤状态
     */
    public String getCurrentStep() {
        for (int i = 0; i < steps.size(); i++) {
            if ("in_progress".equals(stepStatus.get(i))) {
                return steps.get(i);
            }
        }
        return "";
    }

    /**
     * 获取当前进行中的步骤索引。
     */
    public Integer getCurrentStepIndex() {
        if (steps == null || stepStatus == null) {
            return null;
        }
        for (int i = 0; i < steps.size() && i < stepStatus.size(); i++) {
            if ("in_progress".equals(stepStatus.get(i))) {
                return i;
            }
        }
        return null;
    }

    /**
     * 更新当前task为 completed，下一个task为 in_progress
     */
    public void stepPlan() {
        if (steps.isEmpty()) {
            return;
        }
        if (getCurrentStep().isEmpty()) {
            updateStepStatus(0, "in_progress", "");
            return;
        }
        for (int i = 0; i < steps.size(); i++) {
            if ("in_progress".equals(stepStatus.get(i))) {
                updateStepStatus(i, "completed", "");
                if (i + 1 < steps.size()) {
                    updateStepStatus(i + 1, "in_progress", "");
                    break;
                }
            }
        }
    }

    /**
     * 格式化计划显示
     */
    public String format() {
        StringBuilder sb = new StringBuilder();

        // 添加计划标题
        sb.append("Plan: ").append(title).append("\n");
        // 添加步骤列表
        sb.append("Steps:\n");
        for (int i = 0; i < steps.size(); i++) {
            String status = stepStatus.get(i);
            String step = steps.get(i);
            String note = notes.get(i);
            sb.append(String.format("%d. [%s] %s\n", i + 1, status, step));

            if (note != null && !note.isEmpty()) {
                sb.append("   Notes: ").append(note).append("\n");
            }
        }

        return sb.toString();
    }

    private List<String> copyList(List<String> source) {
        return source == null ? new ArrayList<>() : new ArrayList<>(source);
    }
}
