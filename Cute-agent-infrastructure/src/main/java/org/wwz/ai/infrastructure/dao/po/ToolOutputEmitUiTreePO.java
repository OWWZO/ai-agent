package org.wwz.ai.infrastructure.dao.po;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ToolOutputEmitUiTreePO extends AbstractToolOutputPO {

    private String canvasId;
    private Integer salvaged;
    private String treeJson;
}
