package org.wwz.ai.domain.agent.image.service;

import org.wwz.ai.domain.agent.image.model.ImageGenerationExecuteCommand;
import org.wwz.ai.domain.agent.image.model.ImageGenerationExecutionResult;

/**
 * 生图执行内核。
 */
public interface IImageGenerationExecutionKernel {

    ImageGenerationExecutionResult execute(ImageGenerationExecuteCommand command);
}
