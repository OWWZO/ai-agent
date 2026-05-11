package org.wwz.ai.domain.agent.runtime.tool.mcp.runtime;

import io.modelcontextprotocol.client.McpSyncClient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.concurrent.locks.ReentrantLock;

/**
 * MCP 客户端运行时。
 * 一个服务对应一个同步客户端，并通过独占锁保证串行调用。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpClientRuntime {

    /**
     * 当前运行时关联的服务描述。
     */
    private McpServerDescriptor descriptor;

    /**
     * Java MCP 同步客户端。
     */
    @ToString.Exclude
    private McpSyncClient syncClient;

    /**
     * 同一服务上的工具发现与工具执行统一串行，避免客户端复用并发异常。
     */
    @Builder.Default
    @ToString.Exclude
    private ReentrantLock lock = new ReentrantLock();
}
