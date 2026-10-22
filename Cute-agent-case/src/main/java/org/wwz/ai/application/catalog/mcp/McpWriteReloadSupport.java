package org.wwz.ai.application.catalog.mcp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.wwz.ai.application.catalog.ApplicationResult;
import org.wwz.ai.domain.agent.catalog.model.McpRuntimeReloadResult;
import org.wwz.ai.domain.agent.catalog.port.IMcpRuntimeReloadPort;

/**
 * MCP 配置写后刷新。
 * <p>
 * 语义：配置已落库即视为成功，运行时刷新失败只降级为 info 提示——
 * 不能让可选的运行时能力让调用方以为写失败。刷新未改动任何行时直接短路。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public final class McpWriteReloadSupport {

    static final String RELOAD_WARNING = "MCP 配置已写入，但运行时刷新失败";

    private final IMcpRuntimeReloadPort runtimeReloadPort;

    public ApplicationResult<Boolean> afterWrite(boolean changed) {
        if (!changed) {
            return ApplicationResult.success(false);
        }
        try {
            McpRuntimeReloadResult reload = runtimeReloadPort.reloadEnabledMcps();
            if (reload == null || !reload.success()) {
                log.warn("{}: {}", RELOAD_WARNING, reload == null ? "无明确结果" : reload.message());
                return ApplicationResult.success(true, RELOAD_WARNING);
            }
        } catch (Exception e) {
            log.warn("{}", RELOAD_WARNING, e);
            return ApplicationResult.success(true, RELOAD_WARNING);
        }
        return ApplicationResult.success(true);
    }
}
