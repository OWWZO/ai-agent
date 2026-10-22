package org.wwz.ai.domain.agent.image.service;

import org.wwz.ai.domain.agent.image.model.ImageGenerationExecutionResult;

/**
 * 生图批次持久化服务。
 */
public interface IImageGenerationBatchPersistenceService {

    void persistWorkspaceBatch(String requestId, ImageGenerationExecutionResult result);
}
