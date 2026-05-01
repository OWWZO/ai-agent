package org.wwz.ai.infrastructure.dao.po;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * multimodal_agent 输出表 PO。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ToolOutputMultimodalAgentPO extends AbstractToolOutputPO {

    private String summary;

    private String markdownContent;
}
