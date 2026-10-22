package org.wwz.ai.application.agent.image.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 工作台生图生成用例输出。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceImageGenerationView {

    private String data;
    private List<WorkspaceImageFileView> fileInfo;
    private String requestId;
    private String mode;
    private Boolean usedFallback;
    private Object rawResponse;
}
