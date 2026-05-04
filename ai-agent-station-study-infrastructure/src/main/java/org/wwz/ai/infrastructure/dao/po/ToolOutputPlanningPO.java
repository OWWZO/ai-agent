package org.wwz.ai.infrastructure.dao.po;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * planning 输出表 PO。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ToolOutputPlanningPO extends AbstractToolOutputPO {

    private String command;

    private String beforePlanJson;

    private String afterPlanJson;

    private String currentStep;

    private Integer currentStepIndex;

    private Boolean autoAdvanced;

    private Boolean autoFinished;
}
