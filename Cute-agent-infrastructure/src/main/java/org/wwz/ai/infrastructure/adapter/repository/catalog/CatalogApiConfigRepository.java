package org.wwz.ai.infrastructure.adapter.repository.catalog;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.wwz.ai.domain.agent.catalog.model.AiClientApiConfig;
import org.wwz.ai.domain.agent.catalog.port.IAiClientApiConfigRepository;
import org.wwz.ai.infrastructure.dao.catalog.IAiClientApiConfigDao;
import org.wwz.ai.infrastructure.dao.po.catalog.AiClientApiPO;

import java.util.List;
import java.util.Optional;

/** API 配置仓储适配器。 */
@Repository
@RequiredArgsConstructor
public class CatalogApiConfigRepository implements IAiClientApiConfigRepository {
    private final IAiClientApiConfigDao dao;

    public boolean insert(AiClientApiConfig config) { return dao.insert(toPO(config)) > 0; }
    public boolean updateById(AiClientApiConfig config) { return dao.updateById(toPO(config)) > 0; }
    public boolean updateByApiId(AiClientApiConfig config) { return dao.updateByApiId(toPO(config)) > 0; }
    public boolean deleteById(Long id) { return dao.deleteById(id) > 0; }
    public boolean deleteByApiId(String apiId) { return dao.deleteByApiId(apiId) > 0; }
    public Optional<AiClientApiConfig> findById(Long id) { return Optional.ofNullable(toModel(dao.queryById(id))); }
    public Optional<AiClientApiConfig> findByApiId(String apiId) { return Optional.ofNullable(toModel(dao.queryByApiId(apiId))); }
    public List<AiClientApiConfig> findEnabled() { return toModels(dao.queryEnabled()); }
    public List<AiClientApiConfig> findAll() { return toModels(dao.queryAll()); }

    private static AiClientApiPO toPO(AiClientApiConfig c) {
        return AiClientApiPO.builder().id(c.getId()).apiId(c.getApiId()).baseUrl(c.getBaseUrl()).apiKey(c.getApiKey())
                .completionsPath(c.getCompletionsPath()).embeddingsPath(c.getEmbeddingsPath()).status(c.getStatus())
                .createTime(c.getCreateTime()).updateTime(c.getUpdateTime()).build();
    }

    private static AiClientApiConfig toModel(AiClientApiPO p) {
        if (p == null) return null;
        return AiClientApiConfig.builder().id(p.getId()).apiId(p.getApiId()).baseUrl(p.getBaseUrl()).apiKey(p.getApiKey())
                .completionsPath(p.getCompletionsPath()).embeddingsPath(p.getEmbeddingsPath()).status(p.getStatus())
                .createTime(p.getCreateTime()).updateTime(p.getUpdateTime()).build();
    }

    private static List<AiClientApiConfig> toModels(List<AiClientApiPO> rows) {
        return rows == null ? List.of() : rows.stream().map(CatalogApiConfigRepository::toModel).toList();
    }
}
