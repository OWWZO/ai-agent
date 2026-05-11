package org.wwz.ai.trigger.http.agent.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 生图工作台统一图片响应 VO。
 */
@Data
@Builder
public class WorkspaceImageFileRespVO {
    private String fileName;
    private String ossUrl;
    private String domainUrl;
    private String downloadUrl;
    private String previewUrl;
    private Long fileSize;
    private String mimeType;
}
