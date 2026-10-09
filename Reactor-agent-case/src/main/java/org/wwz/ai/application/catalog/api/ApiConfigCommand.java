package org.wwz.ai.application.catalog.api;

/**
 * API 配置管理写命令。
 */
public record ApiConfigCommand(
        Long id,
        String apiId,
        String baseUrl,
        String apiKey,
        String completionsPath,
        String embeddingsPath,
        Integer status) {
}
