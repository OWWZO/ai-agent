package org.wwz.ai.application.agent.image.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工作台生图文件用例输出。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceImageFileView {

    private String fileName;
    private String ossUrl;
    private String domainUrl;
    private String downloadUrl;
    private String previewUrl;
    private Long fileSize;
    private String mimeType;
}
