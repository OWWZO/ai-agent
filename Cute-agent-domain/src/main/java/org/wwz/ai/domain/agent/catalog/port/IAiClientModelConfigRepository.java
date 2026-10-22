package org.wwz.ai.domain.agent.catalog.port;

import org.wwz.ai.domain.agent.catalog.model.AiClientModelConfig;

import java.util.List;
import java.util.Optional;

/**
 * 模型配置持久化端口。
 */
public interface IAiClientModelConfigRepository {

    boolean insert(AiClientModelConfig config);

    boolean updateById(AiClientModelConfig config);

    boolean updateByModelId(AiClientModelConfig config);

    boolean deleteById(Long id);

    boolean deleteByModelId(String modelId);

    Optional<AiClientModelConfig> findById(Long id);

    Optional<AiClientModelConfig> findByModelId(String modelId);

    List<AiClientModelConfig> findAllByModelId(String modelId);

    List<AiClientModelConfig> findByApiId(String apiId);

    List<AiClientModelConfig> findByModelType(String modelType);

    List<AiClientModelConfig> findEnabled();

    List<AiClientModelConfig> findAll();
}
