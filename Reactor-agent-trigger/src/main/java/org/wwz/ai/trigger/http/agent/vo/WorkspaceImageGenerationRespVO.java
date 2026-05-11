package org.wwz.ai.trigger.http.agent.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 生图工作台生成响应 VO。
 */
@Data
@Builder
public class WorkspaceImageGenerationRespVO {
    private String data;
    private List<WorkspaceImageFileRespVO> fileInfo;
    private String requestId;
    private String mode;
    private Boolean usedFallback;
    private Object rawResponse;
}
