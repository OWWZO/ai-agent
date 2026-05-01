package org.wwz.ai.infrastructure.dao.po;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * code_interpreter 输出表 PO。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ToolOutputCodeInterpreterPO extends AbstractToolOutputPO {

    private String codeOutput;

    private String content;

    private String code;

    private String explain;
}
