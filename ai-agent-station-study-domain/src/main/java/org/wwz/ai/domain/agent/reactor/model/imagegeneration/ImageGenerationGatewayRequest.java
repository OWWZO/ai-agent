package org.wwz.ai.domain.agent.reactor.model.imagegeneration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Java -> Python 生图请求模型。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageGenerationGatewayRequest {
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
