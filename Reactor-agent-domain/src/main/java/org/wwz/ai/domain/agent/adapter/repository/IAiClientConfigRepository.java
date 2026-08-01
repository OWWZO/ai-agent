package org.wwz.ai.domain.agent.adapter.repository;

import org.wwz.ai.domain.agent.model.valobj.AiClientAdminRecord;

import java.util.List;
import java.util.Optional;

/**
 * AI 客户端配置仓储端口。
 * Admin CRUD 经 case 调用本接口，禁止 trigger 直连 DAO。
 */
public interface IAiClientConfigRepository {

    boolean insert(AiClientAdminRecord config);

    boolean updateById(AiClientAdminRecord config);

    boolean updateByClientId(AiClientAdminRecord config);

    boolean deleteById(Long id);

    boolean deleteByClientId(String clientId);

    Optional<AiClientAdminRecord> findById(Long id);

    Optional<AiClientAdminRecord> findByClientId(String clientId);

    List<AiClientAdminRecord> findEnabled();

    List<AiClientAdminRecord> findByClientName(String clientName);

    List<AiClientAdminRecord> findAll();
}
