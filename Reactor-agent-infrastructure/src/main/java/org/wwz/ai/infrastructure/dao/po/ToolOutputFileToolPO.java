package org.wwz.ai.infrastructure.dao.po;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * file_tool 输出表 PO。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ToolOutputFileToolPO extends AbstractToolOutputPO {

    private String command;

    private String primaryFileName;

    private String previewUrl;

    private String downloadUrl;
}
