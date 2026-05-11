package org.wwz.ai.domain.agent.reactor.model.imagegeneration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 生图工作台统一文件视图。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceImageFile {
    private String fileName;
    private String ossUrl;
    private String domainUrl;
    private String downloadUrl;
    private String previewUrl;
    private Long fileSize;
    private String mimeType;
}
