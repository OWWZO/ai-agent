package org.wwz.ai.domain.agent.image.port;

import org.wwz.ai.domain.agent.image.model.ImageGenerationGatewayRequest;
import org.wwz.ai.domain.agent.image.model.ImageGenerationGatewayResponse;

/**
 * 生图工作台下游调用端口。
 */
public interface IReactorImageGenerationGateway {

    /**
     * 调用下游图片生成服务。
     */
    ImageGenerationGatewayResponse generate(ImageGenerationGatewayRequest request);
}
