package org.wwz.ai.infrastructure.dao.po;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * deep_search 输出表 PO。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ToolOutputDeepSearchPO extends AbstractToolOutputPO {

    private String query;

    private String answerSummary;

    private String stagesJson;
}
