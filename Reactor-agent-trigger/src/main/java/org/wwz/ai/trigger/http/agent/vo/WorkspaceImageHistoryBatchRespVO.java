package org.wwz.ai.trigger.http.agent.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 生图工作台历史批次响应 VO。
 */
@Data
@Builder
public class WorkspaceImageHistoryBatchRespVO {
    private String requestId;
    private String prompt;
    private String mode;
    private String size;
    private Integer batchCount;
    private Integer sourceImageCount;
    private Integer maskImageCount;
    private Boolean usedFallback;
    private LocalDateTime createdAt;
    private List<WorkspaceImageFileRespVO> images;
}
