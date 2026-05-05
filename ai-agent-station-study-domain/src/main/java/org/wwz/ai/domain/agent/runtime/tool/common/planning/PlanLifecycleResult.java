package org.wwz.ai.domain.agent.runtime.tool.common.planning;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.wwz.ai.domain.agent.runtime.dto.Plan;

/**
 * 计划生命周期处理结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanLifecycleResult {

    /**
     * 生命周期动作执行后的最新计划。
     */
    private Plan plan;

    /**
     * 当前可执行步骤。
     */
    private String currentStep;

    /**
     * 当前可执行步骤索引。
     */
    private Integer currentStepIndex;

    /**
     * 是否发生了系统自动推进。
     */
    @Builder.Default
    private Boolean autoAdvanced = Boolean.FALSE;

    /**
     * 是否发生了系统自动结束。
     */
    @Builder.Default
    private Boolean autoFinished = Boolean.FALSE;
}
