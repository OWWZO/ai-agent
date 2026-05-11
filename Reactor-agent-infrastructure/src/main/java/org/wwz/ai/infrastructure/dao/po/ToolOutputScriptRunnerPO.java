package org.wwz.ai.infrastructure.dao.po;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * script_runner_tool 输出表 PO。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ToolOutputScriptRunnerPO extends AbstractToolOutputPO {

    private String skillName;

    private String scriptName;

    private String runtime;

    private Boolean success;

    private Integer exitCode;

    private String stdout;

    private String stderr;

    private String summary;
}
