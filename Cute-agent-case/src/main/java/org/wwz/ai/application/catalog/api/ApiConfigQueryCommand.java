package org.wwz.ai.application.catalog.api;

/**
 * API 配置管理查询命令。
 */
public record ApiConfigQueryCommand(
        String apiId,
        String baseUrl,
        Integer status,
        Integer pageNum,
        Integer pageSize) {
}
