package org.wwz.ai.application.agent.admin;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.api.dto.AiClientQueryRequestDTO;
import org.wwz.ai.api.dto.AiClientRequestDTO;
import org.wwz.ai.api.dto.AiClientResponseDTO;
import org.wwz.ai.domain.agent.adapter.repository.IAiClientConfigRepository;
import org.wwz.ai.domain.agent.model.valobj.AiClientAdminRecord;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AI 客户端配置管理应用服务。
 * 作为 Admin CRUD 模板：trigger 只依赖 case，仓储落 domain port。
 */
@Service
@RequiredArgsConstructor
public class AiClientAdminApplicationService {

    private final IAiClientConfigRepository aiClientConfigRepository;

    public boolean create(AiClientRequestDTO request) {
        AiClientAdminRecord config = toDomain(request);
        LocalDateTime now = LocalDateTime.now();
        config.setCreateTime(now);
        config.setUpdateTime(now);
        return aiClientConfigRepository.insert(config);
    }

    public boolean updateById(AiClientRequestDTO request) {
        if (request == null || request.getId() == null) {
            throw new IllegalArgumentException("ID不能为空");
        }
        AiClientAdminRecord config = toDomain(request);
        config.setUpdateTime(LocalDateTime.now());
        return aiClientConfigRepository.updateById(config);
    }

    public boolean updateByClientId(AiClientRequestDTO request) {
        if (request == null || StringUtils.isBlank(request.getClientId())) {
            throw new IllegalArgumentException("客户端ID不能为空");
        }
        AiClientAdminRecord config = toDomain(request);
        config.setUpdateTime(LocalDateTime.now());
        return aiClientConfigRepository.updateByClientId(config);
    }

    public boolean deleteById(Long id) {
        return aiClientConfigRepository.deleteById(id);
    }

    public boolean deleteByClientId(String clientId) {
        return aiClientConfigRepository.deleteByClientId(clientId);
    }

    public AiClientResponseDTO queryById(Long id) {
        return aiClientConfigRepository.findById(id)
                .map(this::toResponse)
                .orElse(null);
    }

    public AiClientResponseDTO queryByClientId(String clientId) {
        return aiClientConfigRepository.findByClientId(clientId)
                .map(this::toResponse)
                .orElse(null);
    }

    public List<AiClientResponseDTO> queryEnabled() {
        return mapList(aiClientConfigRepository.findEnabled());
    }

    public List<AiClientResponseDTO> queryAll() {
        return mapList(aiClientConfigRepository.findAll());
    }

    public List<AiClientResponseDTO> queryList(AiClientQueryRequestDTO request) {
        List<AiClientAdminRecord> configs;
        if (request != null && StringUtils.isNotBlank(request.getClientId())) {
            configs = aiClientConfigRepository.findByClientId(request.getClientId())
                    .map(List::of)
                    .orElseGet(List::of);
        } else if (request != null && StringUtils.isNotBlank(request.getClientName())) {
            configs = aiClientConfigRepository.findByClientName(request.getClientName());
        } else {
            configs = aiClientConfigRepository.findAll();
        }

        if (request != null && request.getStatus() != null) {
            configs = configs.stream()
                    .filter(client -> request.getStatus().equals(client.getStatus()))
                    .collect(Collectors.toList());
        }

        if (request != null && request.getPageNum() != null && request.getPageSize() != null) {
            int start = (request.getPageNum() - 1) * request.getPageSize();
            int end = Math.min(start + request.getPageSize(), configs.size());
            if (start < configs.size()) {
                configs = configs.subList(start, end);
            } else {
                configs = List.of();
            }
        }

        return mapList(configs);
    }

    private List<AiClientResponseDTO> mapList(List<AiClientAdminRecord> configs) {
        if (configs == null || configs.isEmpty()) {
            return List.of();
        }
        return configs.stream().map(this::toResponse).collect(Collectors.toList());
    }

    private AiClientAdminRecord toDomain(AiClientRequestDTO request) {
        if (request == null) {
            return new AiClientAdminRecord();
        }
        return AiClientAdminRecord.builder()
                .id(request.getId())
                .clientId(request.getClientId())
                .clientName(request.getClientName())
                .description(request.getDescription())
                .status(request.getStatus())
                .build();
    }

    private AiClientResponseDTO toResponse(AiClientAdminRecord config) {
        return AiClientResponseDTO.builder()
                .id(config.getId())
                .clientId(config.getClientId())
                .clientName(config.getClientName())
                .description(config.getDescription())
                .status(config.getStatus())
                .createTime(config.getCreateTime())
                .updateTime(config.getUpdateTime())
                .build();
    }
}
