package org.wwz.ai.application.catalog.mcp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.application.catalog.ApplicationResult;
import org.wwz.ai.application.catalog.mcp.McpConfigCommand;
import org.wwz.ai.application.catalog.mcp.McpConfigQueryCommand;
import org.wwz.ai.domain.agent.catalog.model.AiClientToolMcpConfig;
import org.wwz.ai.domain.agent.catalog.model.McpRuntimeReloadResult;
import org.wwz.ai.domain.agent.catalog.port.IAiClientToolMcpConfigRepository;
import org.wwz.ai.domain.agent.catalog.port.IMcpRuntimeReloadPort;
import org.wwz.ai.types.enums.ResponseCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MCP 配置管理用例。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpConfigApplicationService {

    private static final String RELOAD_WARNING = "MCP 配置已写入，但运行时刷新失败";

    private final IAiClientToolMcpConfigRepository repository;
    private final IMcpRuntimeReloadPort runtimeReloadPort;

    public ApplicationResult<Boolean> create(McpConfigCommand command) {
        try {
            assertMcpIdAvailable(command);
            AiClientToolMcpConfig config = toConfig(command);
            LocalDateTime now = LocalDateTime.now();
            config.setCreateTime(now);
            config.setUpdateTime(now);
            boolean changed = repository.insert(config);
            return afterWrite(changed);
        } catch (IllegalArgumentException e) {
            return ApplicationResult.failure(ResponseCode.ILLEGAL_PARAMETER, e.getMessage(), false);
        } catch (Exception e) {
            log.error("创建 MCP 客户端配置失败", e);
            return ApplicationResult.failure(ResponseCode.UN_ERROR, ResponseCode.UN_ERROR.getInfo(), false);
        }
    }

    public ApplicationResult<Boolean> updateById(McpConfigCommand command) {
        if (command == null || command.id() == null) {
            return ApplicationResult.failure(ResponseCode.ILLEGAL_PARAMETER, "ID不能为空", false);
        }
        try {
            AiClientToolMcpConfig config = toConfig(command);
            config.setUpdateTime(LocalDateTime.now());
            boolean changed = repository.updateById(config);
            return afterWrite(changed);
        } catch (Exception e) {
            log.error("根据 ID 更新 MCP 客户端配置失败", e);
            return ApplicationResult.failure(ResponseCode.UN_ERROR, ResponseCode.UN_ERROR.getInfo(), false);
        }
    }

    public ApplicationResult<Boolean> updateByMcpId(McpConfigCommand command) {
        if (command == null || !StringUtils.isNotBlank(command.mcpId())) {
            return ApplicationResult.failure(ResponseCode.ILLEGAL_PARAMETER, "MCP ID不能为空", false);
        }
        try {
            AiClientToolMcpConfig config = toConfig(command);
            config.setUpdateTime(LocalDateTime.now());
            boolean changed = repository.updateByMcpId(config);
            return afterWrite(changed);
        } catch (Exception e) {
            log.error("根据 MCP ID 更新 MCP 客户端配置失败", e);
            return ApplicationResult.failure(ResponseCode.UN_ERROR, ResponseCode.UN_ERROR.getInfo(), false);
        }
    }

    public ApplicationResult<Boolean> deleteById(Long id) {
        try {
            boolean changed = repository.deleteById(id);
            return afterWrite(changed);
        } catch (Exception e) {
            log.error("根据 ID 删除 MCP 客户端配置失败", e);
            return ApplicationResult.failure(ResponseCode.UN_ERROR, ResponseCode.UN_ERROR.getInfo(), false);
        }
    }

    public ApplicationResult<Boolean> deleteByMcpId(String mcpId) {
        try {
            boolean changed = repository.deleteByMcpId(mcpId);
            return afterWrite(changed);
        } catch (Exception e) {
            log.error("根据 MCP ID 删除 MCP 客户端配置失败", e);
            return ApplicationResult.failure(ResponseCode.UN_ERROR, ResponseCode.UN_ERROR.getInfo(), false);
        }
    }

    public ApplicationResult<AiClientToolMcpConfig> queryById(Long id) {
        try {
            return repository.findById(id)
                    .map(ApplicationResult::success)
                    .orElseGet(() -> ApplicationResult.success(null));
        } catch (Exception e) {
            log.error("根据 ID 查询 MCP 客户端配置失败", e);
            return ApplicationResult.failure(ResponseCode.UN_ERROR, ResponseCode.UN_ERROR.getInfo(), null);
        }
    }

    public ApplicationResult<AiClientToolMcpConfig> queryByMcpId(String mcpId) {
        try {
            return repository.findByMcpId(mcpId)
                    .map(ApplicationResult::success)
                    .orElseGet(() -> ApplicationResult.success(null));
        } catch (Exception e) {
            log.error("根据 MCP ID 查询 MCP 客户端配置失败", e);
            return ApplicationResult.failure(ResponseCode.UN_ERROR, ResponseCode.UN_ERROR.getInfo(), null);
        }
    }

    public ApplicationResult<List<AiClientToolMcpConfig>> queryAll() {
        return queryList(repository::findAll, "查询所有 MCP 客户端配置失败");
    }

    public ApplicationResult<List<AiClientToolMcpConfig>> queryByStatus(Integer status) {
        return queryList(() -> repository.findByStatus(status), "根据状态查询 MCP 客户端配置失败");
    }

    public ApplicationResult<List<AiClientToolMcpConfig>> queryByTransportType(String transportType) {
        return queryList(() -> repository.findByTransportType(transportType), "根据传输类型查询 MCP 客户端配置失败");
    }

    public ApplicationResult<List<AiClientToolMcpConfig>> queryEnabled() {
        return queryList(repository::findEnabled, "查询启用的 MCP 客户端配置失败");
    }

    public ApplicationResult<List<AiClientToolMcpConfig>> queryList(McpConfigQueryCommand command) {
        try {
            List<AiClientToolMcpConfig> mcps;
            if (StringUtils.isNotBlank(command.mcpId())) {
                AiClientToolMcpConfig single = repository.findByMcpId(command.mcpId()).orElse(null);
                mcps = single == null ? List.of() : List.of(single);
            } else if (command.status() != null) {
                mcps = repository.findByStatus(command.status());
            } else if (StringUtils.isNotBlank(command.transportType())) {
                mcps = repository.findByTransportType(command.transportType());
            } else {
                mcps = repository.findAll();
            }

            if (StringUtils.isNotBlank(command.mcpName())) {
                mcps = mcps.stream()
                        .filter(mcp -> mcp.getMcpName() != null && mcp.getMcpName().contains(command.mcpName()))
                        .toList();
            }
            return ApplicationResult.success(mcps);
        } catch (Exception e) {
            log.error("根据查询条件查询 MCP 客户端配置列表失败", e);
            return ApplicationResult.failure(ResponseCode.UN_ERROR, ResponseCode.UN_ERROR.getInfo(), null);
        }
    }

    private ApplicationResult<List<AiClientToolMcpConfig>> queryList(
            java.util.function.Supplier<List<AiClientToolMcpConfig>> query,
            String errorMessage) {
        try {
            return ApplicationResult.success(query.get());
        } catch (Exception e) {
            log.error(errorMessage, e);
            return ApplicationResult.failure(ResponseCode.UN_ERROR, ResponseCode.UN_ERROR.getInfo(), null);
        }
    }

    private static AiClientToolMcpConfig toConfig(McpConfigCommand command) {
        return AiClientToolMcpConfig.builder()
                .id(command.id())
                .mcpId(command.mcpId())
                .mcpName(command.mcpName())
                .transportType(command.transportType())
                .transportConfig(command.transportConfig())
                .requestTimeout(command.requestTimeout())
                .status(command.status())
                .build();
    }

    private ApplicationResult<Boolean> afterWrite(boolean changed) {
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

    private void assertMcpIdAvailable(McpConfigCommand command) {
        if (command != null && StringUtils.isNotBlank(command.mcpId())
                && repository.findByMcpId(command.mcpId()).isPresent()) {
            throw new IllegalArgumentException("MCP ID 已存在: " + command.mcpId());
        }
    }
}
