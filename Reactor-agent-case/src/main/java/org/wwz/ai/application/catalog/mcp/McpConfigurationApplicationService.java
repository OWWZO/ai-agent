package org.wwz.ai.application.catalog.mcp;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.wwz.ai.domain.agent.catalog.model.AiClientToolMcpConfig;
import org.wwz.ai.domain.agent.catalog.port.IAiClientToolMcpConfigRepository;

import java.time.LocalDateTime;

/**
 * 普通目录创建 MCP 的持久化事务边界。
 */
@Service
@RequiredArgsConstructor
public class McpConfigurationApplicationService {

    private final IAiClientToolMcpConfigRepository repository;

    @Transactional(rollbackFor = Exception.class)
    public boolean createMcp(CatalogMcpCreateCommand command) {
        String mcpId = requireText(command == null ? null : command.mcpId(), "MCP ID 不能为空");
        String mcpName = requireText(command.mcpName(), "MCP 名称不能为空");
        if (repository.findByMcpId(mcpId).isPresent()) {
            throw new IllegalArgumentException("MCP ID 已存在: " + mcpId);
        }
        AiClientToolMcpConfig config = AiClientToolMcpConfig.builder()
                .mcpId(mcpId)
                .mcpName(mcpName)
                .transportType(defaultIfBlank(command.transportType(), "streamable_http"))
                .transportConfig(command.transportConfig())
                .requestTimeout(command.requestTimeout() == null ? 5 : command.requestTimeout())
                .status(normalizeStatus(command.status()))
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        if (!repository.insert(config)) {
            throw new IllegalStateException("MCP 写入失败");
        }
        return true;
    }

    private static int normalizeStatus(Integer status) {
        return status != null && status == 0 ? 0 : 1;
    }

    private static String requireText(String value, String message) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String defaultIfBlank(String value, String fallback) {
        return StringUtils.isBlank(value) ? fallback : value.trim();
    }
}
