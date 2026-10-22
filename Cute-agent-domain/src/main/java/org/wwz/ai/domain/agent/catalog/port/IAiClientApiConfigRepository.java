package org.wwz.ai.domain.agent.catalog.port;

import org.wwz.ai.domain.agent.catalog.model.AiClientApiConfig;

import java.util.List;
import java.util.Optional;

/**
 * API 配置持久化端口。
 */
public interface IAiClientApiConfigRepository {

    boolean insert(AiClientApiConfig config);

    boolean updateById(AiClientApiConfig config);

    boolean updateByApiId(AiClientApiConfig config);

    boolean deleteById(Long id);

    boolean deleteByApiId(String apiId);

    Optional<AiClientApiConfig> findById(Long id);

    Optional<AiClientApiConfig> findByApiId(String apiId);

    List<AiClientApiConfig> findEnabled();

    List<AiClientApiConfig> findAll();
}
