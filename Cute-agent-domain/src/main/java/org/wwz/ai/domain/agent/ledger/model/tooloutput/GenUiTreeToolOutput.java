package org.wwz.ai.domain.agent.ledger.model.tooloutput;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * emit_ui_tree 工具的终态结构化输出。
 *
 * tree 是画布初始展示基准，后续 emit_ui_patch 通过同一 canvasId 叠加；该模型只
 * 负责承载投影所需数据，不直接执行组件校验或渲染。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenUiTreeToolOutput implements ToolStructuredOutput {

    private Map<String, Object> tree;

    private String canvasId;

    private Boolean salvaged;

    @Override
    public String getToolName() {
        return ToolOutputNames.EMIT_UI_TREE;
    }
}
