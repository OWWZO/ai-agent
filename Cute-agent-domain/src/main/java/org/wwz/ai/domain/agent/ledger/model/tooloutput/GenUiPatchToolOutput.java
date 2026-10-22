package org.wwz.ai.domain.agent.ledger.model.tooloutput;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * emit_ui_patch 工具的终态结构化输出。
 *
 * patches 是供历史投影重放的操作记录，canvasId/seq 用于把增量更新关联到正确的
 * GenUI 画布；它不是第二套展示账本。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenUiPatchToolOutput implements ToolStructuredOutput {

    @Builder.Default
    private List<Map<String, Object>> patches = new ArrayList<>();

    private String canvasId;

    private Integer seq;

    @Override
    public String getToolName() {
        return ToolOutputNames.EMIT_UI_PATCH;
    }
}
