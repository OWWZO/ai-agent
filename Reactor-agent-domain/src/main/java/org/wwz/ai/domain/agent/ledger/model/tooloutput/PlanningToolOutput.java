package org.wwz.ai.domain.agent.ledger.model.tooloutput;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.wwz.ai.domain.agent.runtime.dto.Plan;

/**
 * planning 工具结构化输出。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanningToolOutput implements ToolStructuredOutput {

    /**
     * 本轮执行的命令。
     */
    private String command;

    /**
     * 执行前计划快照。
     */
    private Plan beforePlan;

    /**
     * 执行后计划快照。
     */
    private Plan afterPlan;

    /**
     * 当前可执行步骤。
     */
    private String currentStep;

    /**
     * 当前可执行步骤索引。
     */
    private Integer currentStepIndex;

    /**
     * 是否发生自动推进。
     */
    @Builder.Default
    private Boolean autoAdvanced = Boolean.FALSE;

    /**
     * 是否发生自动结束。
     */
    @Builder.Default
    private Boolean autoFinished = Boolean.FALSE;

    @Override
    public String getToolName() {
        return ToolOutputNames.PLANNING;
    }
}
