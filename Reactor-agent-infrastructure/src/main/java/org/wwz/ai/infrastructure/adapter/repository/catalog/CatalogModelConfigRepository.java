package org.wwz.ai.infrastructure.adapter.repository.catalog;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.wwz.ai.domain.agent.catalog.model.AiClientModelConfig;
import org.wwz.ai.domain.agent.catalog.port.IAiClientModelConfigRepository;
import org.wwz.ai.infrastructure.dao.catalog.IAiClientModelConfigDao;
import org.wwz.ai.infrastructure.dao.po.catalog.AiClientModelPO;

import java.util.List;
import java.util.Optional;

/** 模型配置仓储适配器。 */
@Repository
@RequiredArgsConstructor
public class CatalogModelConfigRepository implements IAiClientModelConfigRepository {
    private final IAiClientModelConfigDao dao;

    public boolean insert(AiClientModelConfig c) { return dao.insert(toPO(c)) > 0; }
    public boolean updateById(AiClientModelConfig c) { return dao.updateById(toPO(c)) > 0; }
    public boolean updateByModelId(AiClientModelConfig c) { return dao.updateByModelId(toPO(c)) > 0; }
    public boolean deleteById(Long id) { return dao.deleteById(id) > 0; }
    public boolean deleteByModelId(String id) { return dao.deleteByModelId(id) > 0; }
    public Optional<AiClientModelConfig> findById(Long id) { return Optional.ofNullable(toModel(dao.queryById(id))); }
    public Optional<AiClientModelConfig> findByModelId(String id) { return Optional.ofNullable(toModel(dao.queryByModelId(id))); }
    public List<AiClientModelConfig> findAllByModelId(String id) { return toModels(dao.queryAllByModelId(id)); }
    public List<AiClientModelConfig> findByApiId(String id) { return toModels(dao.queryByApiId(id)); }
    public List<AiClientModelConfig> findByModelType(String type) { return toModels(dao.queryByModelType(type)); }
    public List<AiClientModelConfig> findEnabled() { return toModels(dao.queryEnabled()); }
    public List<AiClientModelConfig> findAll() { return toModels(dao.queryAll()); }

    private static AiClientModelPO toPO(AiClientModelConfig c) {
        return AiClientModelPO.builder().id(c.getId()).modelId(c.getModelId()).apiId(c.getApiId()).modelName(c.getModelName())
                .modelType(c.getModelType()).modelUsage(c.getModelUsage()).supportsThinking(c.getSupportsThinking())
                .contextWindow(c.getContextWindow()).status(c.getStatus()).createTime(c.getCreateTime())
                .updateTime(c.getUpdateTime()).build();
    }

    private static AiClientModelConfig toModel(AiClientModelPO p) {
        if (p == null) return null;
        return AiClientModelConfig.builder().id(p.getId()).modelId(p.getModelId()).apiId(p.getApiId()).modelName(p.getModelName())
                .modelType(p.getModelType()).modelUsage(p.getModelUsage()).supportsThinking(p.getSupportsThinking())
                .contextWindow(p.getContextWindow()).status(p.getStatus()).createTime(p.getCreateTime())
                .updateTime(p.getUpdateTime()).build();
    }

    private static List<AiClientModelConfig> toModels(List<AiClientModelPO> rows) {
        return rows == null ? List.of() : rows.stream().map(CatalogModelConfigRepository::toModel).toList();
    }
}
