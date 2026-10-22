package org.wwz.ai.domain.agent.catalog.model;

/**
 * MCP 运行时刷新结果。失败信息保持为领域可理解的摘要，不暴露客户端或 DAO 异常类型。
 */
public record McpRuntimeReloadResult(boolean success, String message) {

    public static McpRuntimeReloadResult succeeded() {
        return new McpRuntimeReloadResult(true, "MCP runtime reloaded");
    }

    public static McpRuntimeReloadResult failed(String message) {
        return new McpRuntimeReloadResult(false,
                message == null || message.isBlank() ? "MCP runtime reload failed" : message);
    }
}
