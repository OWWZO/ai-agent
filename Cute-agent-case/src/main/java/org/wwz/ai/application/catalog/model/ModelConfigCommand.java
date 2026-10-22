package org.wwz.ai.application.catalog.model;

/**
 * 模型配置管理写命令。
 */
public record ModelConfigCommand(
        Long id,
        String modelId,
        String apiId,
        String modelName,
        String modelType,
        String modelUsage,
        Integer supportsThinking,
        Integer contextWindow,
        Integer status) {
}
