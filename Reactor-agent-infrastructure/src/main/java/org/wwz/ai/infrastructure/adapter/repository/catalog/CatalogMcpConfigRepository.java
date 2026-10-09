package org.wwz.ai.infrastructure.adapter.repository.catalog;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.wwz.ai.domain.agent.catalog.model.AiClientToolMcpConfig;
import org.wwz.ai.domain.agent.catalog.port.IAiClientToolMcpConfigRepository;
import org.wwz.ai.infrastructure.dao.catalog.IAiClientToolMcpConfigDao;
import org.wwz.ai.infrastructure.dao.po.catalog.AiClientToolMcpPO;

import java.util.List;
import java.util.Optional;

/** MCP 配置仓储适配器。 */
@Repository
@RequiredArgsConstructor
public class CatalogMcpConfigRepository implements IAiClientToolMcpConfigRepository {
    private final IAiClientToolMcpConfigDao dao;

    public boolean insert(AiClientToolMcpConfig c) { return dao.insert(toPO(c)) > 0; }
    public boolean updateById(AiClientToolMcpConfig c) { return dao.updateById(toPO(c)) > 0; }
    public boolean updateByMcpId(AiClientToolMcpConfig c) { return dao.updateByMcpId(toPO(c)) > 0; }
    public boolean deleteById(Long id) { return dao.deleteById(id) > 0; }
    public boolean deleteByMcpId(String id) { return dao.deleteByMcpId(id) > 0; }
    public Optional<AiClientToolMcpConfig> findById(Long id) { return Optional.ofNullable(toModel(dao.queryById(id))); }
    public Optional<AiClientToolMcpConfig> findByMcpId(String id) { return Optional.ofNullable(toModel(dao.queryByMcpId(id))); }
    public List<AiClientToolMcpConfig> findAll() { return toModels(dao.queryAll()); }
    public List<AiClientToolMcpConfig> findByStatus(Integer status) { return toModels(dao.queryByStatus(status)); }
    public List<AiClientToolMcpConfig> findByTransportType(String type) { return toModels(dao.queryByTransportType(type)); }
    public List<AiClientToolMcpConfig> findEnabled() { return toModels(dao.queryEnabled()); }

    private static AiClientToolMcpPO toPO(AiClientToolMcpConfig c) {
        return AiClientToolMcpPO.builder().id(c.getId()).mcpId(c.getMcpId()).mcpName(c.getMcpName())
                .transportType(c.getTransportType()).transportConfig(c.getTransportConfig()).requestTimeout(c.getRequestTimeout())
                .status(c.getStatus()).createTime(c.getCreateTime()).updateTime(c.getUpdateTime()).build();
    }

    private static AiClientToolMcpConfig toModel(AiClientToolMcpPO p) {
        if (p == null) return null;
        return AiClientToolMcpConfig.builder().id(p.getId()).mcpId(p.getMcpId()).mcpName(p.getMcpName())
                .transportType(p.getTransportType()).transportConfig(p.getTransportConfig()).requestTimeout(p.getRequestTimeout())
                .status(p.getStatus()).createTime(p.getCreateTime()).updateTime(p.getUpdateTime()).build();
    }

    private static List<AiClientToolMcpConfig> toModels(List<AiClientToolMcpPO> rows) {
        return rows == null ? List.of() : rows.stream().map(CatalogMcpConfigRepository::toModel).toList();
    }
}
