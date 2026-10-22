package org.wwz.ai.infrastructure.dao.po;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ToolOutputCanvasPublishPO extends AbstractToolOutputPO {

    private String title;
    private String mode;
    private String primaryFileName;
    private String previewUrl;
    private String downloadUrl;
    private Integer openInPanel;
    private Integer salvaged;
}
