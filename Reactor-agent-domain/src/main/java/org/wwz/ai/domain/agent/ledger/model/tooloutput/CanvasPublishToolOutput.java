package org.wwz.ai.domain.agent.ledger.model.tooloutput;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * canvas_publish terminal structured output.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CanvasPublishToolOutput implements ToolStructuredOutput {

    private String title;

    private String mode;

    private String primaryFileName;

    private String previewUrl;

    private String downloadUrl;

    private Boolean openInPanel;

    private Boolean salvaged;

    @Builder.Default
    private List<ToolFileRef> fileRefs = new ArrayList<>();

    @Override
    public String getToolName() {
        return ToolOutputNames.CANVAS_PUBLISH;
    }
}
