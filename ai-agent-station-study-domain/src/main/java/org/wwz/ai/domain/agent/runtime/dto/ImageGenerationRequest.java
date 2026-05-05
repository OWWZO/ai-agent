package org.wwz.ai.domain.agent.runtime.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 图片生成工具请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageGenerationRequest {
    private String requestId;
    private String prompt;
    private String mode;
    private List<String> fileNames;
    private List<String> maskFileNames;
    private String fileName;
    private String fileDescription;
    private String model;
    private String size;
    private Integer n;
    private Integer timeoutSeconds;
    private Boolean stream;
}
