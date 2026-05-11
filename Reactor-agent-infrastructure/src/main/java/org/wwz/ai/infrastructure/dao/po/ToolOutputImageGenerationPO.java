package org.wwz.ai.infrastructure.dao.po;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * image_generation_tool 输出表 PO。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ToolOutputImageGenerationPO extends AbstractToolOutputPO {

    private String prompt;

    private String mode;

    private String summary;

    private String size;

    private Integer batchCount;

    private Integer sourceImageCount;

    private Integer maskImageCount;

    private Boolean usedFallback;
}
