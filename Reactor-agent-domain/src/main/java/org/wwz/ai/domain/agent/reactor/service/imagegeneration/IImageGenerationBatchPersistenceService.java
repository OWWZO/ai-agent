package org.wwz.ai.domain.agent.reactor.service.imagegeneration;

import org.wwz.ai.domain.agent.reactor.model.imagegeneration.ImageGenerationExecutionResult;

/**
 * 生图批次持久化服务。
 */
public interface IImageGenerationBatchPersistenceService {

    void persistWorkspaceBatch(String requestId, ImageGenerationExecutionResult result);
}
