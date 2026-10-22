package org.wwz.ai.domain.agent.runtime.stream;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agent 计划事件的 typed payload。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanStreamPayload {

    private String title;
    private List<String> stages;
    private List<String> steps;
    private List<String> stepStatus;
    private List<String> notes;

    /**
     * 将模型返回的“执行顺序 N. 阶段：步骤”统一拆成前端使用的 stages/steps。
     */
    public static PlanStreamPayload formatSteps(PlanStreamPayload plan) {
        if (plan == null) {
            return null;
        }
        PlanStreamPayload formatted = PlanStreamPayload.builder()
                .title(plan.getTitle())
                .steps(new ArrayList<>())
                .stages(new ArrayList<>())
                .stepStatus(new ArrayList<>())
                .notes(new ArrayList<>())
                .build();
        Pattern pattern = Pattern.compile("执行顺序(\\d+)\\.\\s?([\\w\\W]*)\\s?[：:](.*)");
        List<String> sourceSteps = plan.getSteps() == null ? List.of() : plan.getSteps();
        for (int i = 0; i < sourceSteps.size(); i++) {
            addAt(formatted.getStepStatus(), plan.getStepStatus(), i);
            addAt(formatted.getNotes(), plan.getNotes(), i);

            String step = sourceSteps.get(i);
            Matcher matcher = pattern.matcher(step == null ? "" : step);
            if (matcher.find()) {
                formatted.getSteps().add(matcher.group(3).trim());
                formatted.getStages().add(matcher.group(2).trim());
            } else {
                formatted.getSteps().add(step);
                formatted.getStages().add("");
            }
        }
        return formatted;
    }

    private static void addAt(List<String> target, List<String> source, int index) {
        target.add(source != null && index < source.size() ? source.get(index) : null);
    }
}
