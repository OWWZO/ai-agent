package org.wwz.ai.application.catalog.model;

/**
 * 模型配置管理查询命令。
 */
public record ModelConfigQueryCommand(
        String modelId,
        String apiId,
        String modelType,
        Integer status) {
}
