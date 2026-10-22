package org.wwz.ai.domain.agent.runtime.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件工具响应模型，承载文件操作结果和产物信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileResponse {
    private String requestId;
    private String ossUrl;
    private String domainUrl;
    private String fileName;
    private Integer fileSize;
}
