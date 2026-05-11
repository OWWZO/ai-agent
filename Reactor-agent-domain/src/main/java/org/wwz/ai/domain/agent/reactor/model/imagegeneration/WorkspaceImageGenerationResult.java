package org.wwz.ai.domain.agent.reactor.model.imagegeneration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 生图工作台生成结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceImageGenerationResult {
    private String data;
    private List<WorkspaceImageFile> fileInfo;
    private String requestId;
    private String mode;
    private Boolean usedFallback;
    private Object rawResponse;
}
