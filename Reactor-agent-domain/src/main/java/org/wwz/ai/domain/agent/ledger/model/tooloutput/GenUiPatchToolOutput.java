package org.wwz.ai.domain.agent.ledger.model.tooloutput;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * emit_ui_patch terminal structured output (not a dedicated DB table yet).
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
