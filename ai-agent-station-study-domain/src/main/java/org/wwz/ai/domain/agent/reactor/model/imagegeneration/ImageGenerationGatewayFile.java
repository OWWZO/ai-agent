package org.wwz.ai.domain.agent.reactor.model.imagegeneration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 下游图片生成服务返回的单张图片文件信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageGenerationGatewayFile {
    private String fileName;
    private String ossUrl;
    private String domainUrl;
    private String downloadUrl;
    private String previewUrl;
    private Long fileSize;
    private String mimeType;
}
