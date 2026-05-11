package org.wwz.ai.domain.agent.reactor.model.imagegeneration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 下游图片生成服务响应模型。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageGenerationGatewayResponse {
    private String data;
    private List<ImageGenerationGatewayFile> fileInfo;
    private String requestId;
    private String mode;
    private Boolean usedFallback;
    private Object rawResponse;
}
