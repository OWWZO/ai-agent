package org.wwz.ai.domain.agent.reactor.model.imagegeneration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 统一的生图执行结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageGenerationExecutionResult {

    private String requestId;

    private String prompt;

    private String mode;

    private String summary;

    private String size;

    private Integer batchCount;

    private Integer sourceImageCount;

    private Integer maskImageCount;

    private Boolean usedFallback;

    private Object rawResponse;

    @Builder.Default
    private List<WorkspaceImageFile> files = new ArrayList<>();
}
