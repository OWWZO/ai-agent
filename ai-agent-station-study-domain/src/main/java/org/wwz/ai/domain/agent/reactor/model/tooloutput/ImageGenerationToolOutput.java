package org.wwz.ai.domain.agent.reactor.model.tooloutput;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * image_generation_tool 终态结构化输出。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageGenerationToolOutput implements ToolStructuredOutput {

    private String prompt;

    private String mode;

    private String summary;

    @Builder.Default
    private List<ToolFileRef> fileRefs = new ArrayList<>();

    @Override
    public String getToolName() {
        return ToolOutputNames.IMAGE_GENERATION;
    }
}
