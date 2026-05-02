package org.wwz.ai.domain.agent.reactor.service.imagegeneration;

import org.wwz.ai.domain.agent.reactor.model.imagegeneration.ImageGenerationExecuteCommand;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.ImageGenerationExecutionResult;

/**
 * 生图执行内核。
 */
public interface IImageGenerationExecutionKernel {

    ImageGenerationExecutionResult execute(ImageGenerationExecuteCommand command);
}
