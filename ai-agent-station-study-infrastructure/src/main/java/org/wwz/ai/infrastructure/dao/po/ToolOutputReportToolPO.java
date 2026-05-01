package org.wwz.ai.infrastructure.dao.po;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * report_tool 输出表 PO。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ToolOutputReportToolPO extends AbstractToolOutputPO {

    private String fileType;

    private String summary;

    private String content;
}
