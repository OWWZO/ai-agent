package org.wwz.ai.domain.agent.ledger.model.tooloutput;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * emit_ui_tree terminal structured output (not a dedicated DB table yet).
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
