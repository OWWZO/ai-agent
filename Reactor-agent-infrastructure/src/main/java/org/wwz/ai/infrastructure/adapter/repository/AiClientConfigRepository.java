package org.wwz.ai.infrastructure.adapter.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.wwz.ai.domain.agent.adapter.repository.IAiClientConfigRepository;
import org.wwz.ai.domain.agent.model.valobj.AiClientAdminRecord;
import org.wwz.ai.infrastructure.dao.IAiClientDao;
import org.wwz.ai.infrastructure.dao.po.AiClient;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * AI 客户端配置仓储实现。
 */
@Repository
@RequiredArgsConstructor
public class AiClientConfigRepository implements IAiClientConfigRepository {

    private final IAiClientDao aiClientDao;

    @Override
    public boolean insert(AiClientAdminRecord config) {
        return aiClientDao.insert(toPo(config)) > 0;
    }

    @Override
    public boolean updateById(AiClientAdminRecord config) {
        return aiClientDao.updateById(toPo(config)) > 0;
    }

    @Override
    public boolean updateByClientId(AiClientAdminRecord config) {
        return aiClientDao.updateByClientId(toPo(config)) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return aiClientDao.deleteById(id) > 0;
    }

    @Override
    public boolean deleteByClientId(String clientId) {
        return aiClientDao.deleteByClientId(clientId) > 0;
    }

    @Override
    public Optional<AiClientAdminRecord> findById(Long id) {
        return Optional.ofNullable(toDomain(aiClientDao.queryById(id)));
    }

    @Override
    public Optional<AiClientAdminRecord> findByClientId(String clientId) {
        return Optional.ofNullable(toDomain(aiClientDao.queryByClientId(clientId)));
    }

    @Override
    public List<AiClientAdminRecord> findEnabled() {
        return mapList(aiClientDao.queryEnabledClients());
    }

    @Override
    public List<AiClientAdminRecord> findByClientName(String clientName) {
        return mapList(aiClientDao.queryByClientName(clientName));
    }

    @Override
    public List<AiClientAdminRecord> findAll() {
        return mapList(aiClientDao.queryAll());
    }

    private List<AiClientAdminRecord> mapList(List<AiClient> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream().map(this::toDomain).collect(Collectors.toList());
    }

    private AiClient toPo(AiClientAdminRecord config) {
        if (config == null) {
            return null;
        }
        return AiClient.builder()
                .id(config.getId())
                .clientId(config.getClientId())
                .clientName(config.getClientName())
                .description(config.getDescription())
                .status(config.getStatus())
                .createTime(config.getCreateTime())
                .updateTime(config.getUpdateTime())
                .build();
    }

    private AiClientAdminRecord toDomain(AiClient po) {
        if (po == null) {
            return null;
        }
        return AiClientAdminRecord.builder()
                .id(po.getId())
                .clientId(po.getClientId())
                .clientName(po.getClientName())
                .description(po.getDescription())
                .status(po.getStatus())
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .build();
    }
}
