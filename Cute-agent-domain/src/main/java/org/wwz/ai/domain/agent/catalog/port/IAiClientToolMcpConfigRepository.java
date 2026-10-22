package org.wwz.ai.domain.agent.catalog.port;

import org.wwz.ai.domain.agent.catalog.model.AiClientToolMcpConfig;

import java.util.List;
import java.util.Optional;

/**
 * MCP 配置持久化端口。
 */
public interface IAiClientToolMcpConfigRepository {

    boolean insert(AiClientToolMcpConfig config);

    boolean updateById(AiClientToolMcpConfig config);

    boolean updateByMcpId(AiClientToolMcpConfig config);

    boolean deleteById(Long id);

    boolean deleteByMcpId(String mcpId);

    Optional<AiClientToolMcpConfig> findById(Long id);

    Optional<AiClientToolMcpConfig> findByMcpId(String mcpId);

    List<AiClientToolMcpConfig> findAll();

    List<AiClientToolMcpConfig> findByStatus(Integer status);

    List<AiClientToolMcpConfig> findByTransportType(String transportType);

    List<AiClientToolMcpConfig> findEnabled();
}
