package org.wwz.ai.infrastructure.dao.po;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * data_analysis 输出表 PO。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ToolOutputDataAnalysisPO extends AbstractToolOutputPO {

    private String task;

    private String summary;

    private String content;
}
