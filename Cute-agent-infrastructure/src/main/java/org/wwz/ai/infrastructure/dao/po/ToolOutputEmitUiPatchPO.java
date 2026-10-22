package org.wwz.ai.infrastructure.dao.po;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ToolOutputEmitUiPatchPO extends AbstractToolOutputPO {

    private String canvasId;
    private Integer seq;
    private String patchesJson;
}
