package org.wwz.ai.application.catalog.mcp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.application.catalog.ApplicationResult;
import org.wwz.ai.domain.agent.catalog.model.AiClientToolMcpConfig;
import org.wwz.ai.domain.agent.catalog.port.IAiClientToolMcpConfigRepository;
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

    private final IAiClientToolMcpConfigRepository repository;
    private final McpWriteReloadSupport writeReloadSupport;

    public ApplicationResult<Boolean> create(McpConfigCommand command) {
        try {
            assertMcpIdAvailable(command);
            AiClientToolMcpConfig config = toConfig(command);
            LocalDateTime now = LocalDateTime.now();
            config.setCreateTime(now);
            config.setUpdateTime(now);
            // 与普通目录创建共用同一组默认值，避免两条创建路径对同一个 MCP
            // 给出不同的 transportType / requestTimeout / status 语义。
            applyWriteDefaults(config);
            boolean changed = repository.insert(config);
            return writeReloadSupport.afterWrite(changed);
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
            applyWriteDefaults(config);
            boolean changed = repository.updateById(config);
            return writeReloadSupport.afterWrite(changed);
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
            applyWriteDefaults(config);
            boolean changed = repository.updateByMcpId(config);
            return writeReloadSupport.afterWrite(changed);
        } catch (Exception e) {
            log.error("根据 MCP ID 更新 MCP 客户端配置失败", e);
            return ApplicationResult.failure(ResponseCode.UN_ERROR, ResponseCode.UN_ERROR.getInfo(), false);
        }
    }

    public ApplicationResult<Boolean> deleteById(Long id) {
        try {
            boolean changed = repository.deleteById(id);
            return writeReloadSupport.afterWrite(changed);
        } catch (Exception e) {
            log.error("根据 ID 删除 MCP 客户端配置失败", e);
            return ApplicationResult.failure(ResponseCode.UN_ERROR, ResponseCode.UN_ERROR.getInfo(), false);
        }
    }

    public ApplicationResult<Boolean> deleteByMcpId(String mcpId) {
        try {
            boolean changed = repository.deleteByMcpId(mcpId);
            return writeReloadSupport.afterWrite(changed);
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

    /**
     * 补齐写入默认值，create / update 都必须走。
     * <p>
     * Mapper 的 UPDATE 是静态赋值（{@code transport_type = #{transportType}}），
     * 不补默认值就会把 NULL 写进可空列，之后 buildDescriptor 三个分支全部落空，
     * 该 MCP 从运行时静默消失。
     */
    private static void applyWriteDefaults(AiClientToolMcpConfig config) {
        if (StringUtils.isBlank(config.getTransportType())) {
            config.setTransportType(McpDefaults.DEFAULT_TRANSPORT_TYPE);
        }
        if (config.getRequestTimeout() == null) {
            config.setRequestTimeout(McpDefaults.DEFAULT_REQUEST_TIMEOUT);
        }
        config.setStatus(McpDefaults.normalizeStatus(config.getStatus()));
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


    private void assertMcpIdAvailable(McpConfigCommand command) {
        if (command != null && StringUtils.isNotBlank(command.mcpId())
                && repository.findByMcpId(command.mcpId()).isPresent()) {
            throw new IllegalArgumentException("MCP ID 已存在: " + command.mcpId());
        }
    }
}
