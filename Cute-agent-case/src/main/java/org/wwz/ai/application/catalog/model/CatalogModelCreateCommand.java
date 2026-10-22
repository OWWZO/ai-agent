package org.wwz.ai.application.catalog.model;

/**
 * 普通目录入口创建模型及其 API 的应用命令。
 */
public record CatalogModelCreateCommand(
        String apiId,
        String baseUrl,
        String apiKey,
        String completionsPath,
        String embeddingsPath,
        String modelId,
        String modelName,
        String modelType,
        String modelUsage,
        Integer supportsThinking,
        Integer contextWindow,
        Integer status) {
}
