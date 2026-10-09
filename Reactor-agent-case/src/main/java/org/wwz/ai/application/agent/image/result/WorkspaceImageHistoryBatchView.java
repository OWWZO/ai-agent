package org.wwz.ai.application.agent.image.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工作台生图历史批次用例输出。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceImageHistoryBatchView {

    private String requestId;
    private String prompt;
    private String mode;
    private String size;
    private Integer batchCount;
    private Integer sourceImageCount;
    private Integer maskImageCount;
    private Boolean usedFallback;
    private LocalDateTime createdAt;
    private List<WorkspaceImageFileView> images;
}
