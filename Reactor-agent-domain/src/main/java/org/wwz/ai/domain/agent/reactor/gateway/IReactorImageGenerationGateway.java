package org.wwz.ai.domain.agent.reactor.gateway;

import org.wwz.ai.domain.agent.reactor.model.imagegeneration.ImageGenerationGatewayRequest;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.ImageGenerationGatewayResponse;

/**
 * 生图工作台下游调用端口。
 */
public interface IReactorImageGenerationGateway {

    /**
     * 调用下游图片生成服务。
     */
    ImageGenerationGatewayResponse generate(ImageGenerationGatewayRequest request);
}
