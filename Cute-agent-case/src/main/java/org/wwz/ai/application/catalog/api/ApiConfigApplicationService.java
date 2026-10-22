package org.wwz.ai.application.catalog.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.application.catalog.api.ApiConfigCommand;
import org.wwz.ai.application.catalog.api.ApiConfigQueryCommand;
import org.wwz.ai.application.catalog.ApplicationResult;
import org.wwz.ai.domain.agent.catalog.model.AiClientApiConfig;
import org.wwz.ai.domain.agent.catalog.port.IAiClientApiConfigRepository;
import org.wwz.ai.domain.agent.catalog.port.ICatalogModelRuntimePort;
import org.wwz.ai.types.enums.ResponseCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * API 配置管理用例。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiConfigApplicationService {

    private final IAiClientApiConfigRepository repository;
    private final ICatalogModelRuntimePort modelRuntimePort;

    public ApplicationResult<Boolean> create(ApiConfigCommand command) {
        try {
            assertApiIdAvailable(command);
            LocalDateTime now = LocalDateTime.now();
            AiClientApiConfig config = toConfig(command);
            config.setCreateTime(now);
            config.setUpdateTime(now);
            boolean changed = repository.insert(config);
            if (changed) {
                modelRuntimePort.invalidateAll();
            }
            return ApplicationResult.success(changed);
        } catch (IllegalArgumentException e) {
            return ApplicationResult.failure(ResponseCode.ILLEGAL_PARAMETER, e.getMessage(), false);
        } catch (Exception e) {
            log.error("创建 AI 客户端 API 配置失败", e);
            return ApplicationResult.failure(ResponseCode.UN_ERROR, ResponseCode.UN_ERROR.getInfo(), false);
        }
    }

    public ApplicationResult<Boolean> updateById(ApiConfigCommand command) {
        if (command == null || command.id() == null) {
            return ApplicationResult.failure(ResponseCode.ILLEGAL_PARAMETER, "ID不能为空", false);
        }
        try {
            AiClientApiConfig config = toConfig(command);
            config.setUpdateTime(LocalDateTime.now());
            boolean changed = repository.updateById(config);
            if (changed) {
                modelRuntimePort.invalidateAll();
            }
            return ApplicationResult.success(changed);
        } catch (Exception e) {
            log.error("根据 ID 更新 AI 客户端 API 配置失败", e);
            return ApplicationResult.failure(ResponseCode.UN_ERROR, ResponseCode.UN_ERROR.getInfo(), false);
        }
    }

    public ApplicationResult<Boolean> updateByApiId(ApiConfigCommand command) {
        if (command == null || !StringUtils.isNotBlank(command.apiId())) {
            return ApplicationResult.failure(ResponseCode.ILLEGAL_PARAMETER, "API ID不能为空", false);
        }
        try {
            AiClientApiConfig config = toConfig(command);
            config.setUpdateTime(LocalDateTime.now());
            boolean changed = repository.updateByApiId(config);
            if (changed) {
                modelRuntimePort.invalidateAll();
            }
            return ApplicationResult.success(changed);
        } catch (Exception e) {
            log.error("根据 API ID 更新 AI 客户端 API 配置失败", e);
            return ApplicationResult.failure(ResponseCode.UN_ERROR, ResponseCode.UN_ERROR.getInfo(), false);
        }
    }

    public ApplicationResult<Boolean> deleteById(Long id) {
        try {
            boolean changed = repository.deleteById(id);
            if (changed) {
                modelRuntimePort.invalidateAll();
            }
            return ApplicationResult.success(changed);
        } catch (Exception e) {
            log.error("根据 ID 删除 AI 客户端 API 配置失败", e);
            return ApplicationResult.failure(ResponseCode.UN_ERROR, ResponseCode.UN_ERROR.getInfo(), false);
        }
    }

    public ApplicationResult<Boolean> deleteByApiId(String apiId) {
        try {
            boolean changed = repository.deleteByApiId(apiId);
            if (changed) {
                modelRuntimePort.invalidateAll();
            }
            return ApplicationResult.success(changed);
        } catch (Exception e) {
            log.error("根据 API ID 删除 AI 客户端 API 配置失败", e);
            return ApplicationResult.failure(ResponseCode.UN_ERROR, ResponseCode.UN_ERROR.getInfo(), false);
        }
    }

    public ApplicationResult<AiClientApiConfig> queryById(Long id) {
        try {
            return repository.findById(id)
                    .map(ApplicationResult::success)
                    .orElseGet(() -> ApplicationResult.failure(
                            ResponseCode.UN_ERROR, "未找到对应的AI客户端API配置", null));
        } catch (Exception e) {
            log.error("根据 ID 查询 AI 客户端 API 配置失败", e);
            return ApplicationResult.failure(ResponseCode.UN_ERROR, ResponseCode.UN_ERROR.getInfo(), null);
        }
    }

    public ApplicationResult<AiClientApiConfig> queryByApiId(String apiId) {
        try {
            return repository.findByApiId(apiId)
                    .map(ApplicationResult::success)
                    .orElseGet(() -> ApplicationResult.failure(
                            ResponseCode.UN_ERROR, "未找到对应的AI客户端API配置", null));
        } catch (Exception e) {
            log.error("根据 API ID 查询 AI 客户端 API 配置失败", e);
            return ApplicationResult.failure(ResponseCode.UN_ERROR, ResponseCode.UN_ERROR.getInfo(), null);
        }
    }

    public ApplicationResult<List<AiClientApiConfig>> queryEnabled() {
        try {
            return ApplicationResult.success(repository.findEnabled());
        } catch (Exception e) {
            log.error("查询所有启用的 AI 客户端 API 配置失败", e);
            return ApplicationResult.failure(ResponseCode.UN_ERROR, ResponseCode.UN_ERROR.getInfo(), null);
        }
    }

    public ApplicationResult<List<AiClientApiConfig>> queryList(ApiConfigQueryCommand command) {
        try {
            List<AiClientApiConfig> all = repository.findAll();
            List<AiClientApiConfig> filtered = all.stream()
                    .filter(api -> !StringUtils.isNotBlank(command.apiId()) || api.getApiId().contains(command.apiId()))
                    .filter(api -> !StringUtils.isNotBlank(command.baseUrl()) || api.getBaseUrl().contains(command.baseUrl()))
                    .filter(api -> command.status() == null || api.getStatus().equals(command.status()))
                    .toList();

            int pageNum = command.pageNum() != null ? command.pageNum() : 1;
            int pageSize = command.pageSize() != null ? command.pageSize() : 10;
            int startIndex = (pageNum - 1) * pageSize;
            int endIndex = Math.min(startIndex + pageSize, filtered.size());
            List<AiClientApiConfig> page = startIndex < filtered.size()
                    ? filtered.subList(startIndex, endIndex)
                    : List.of();
            return ApplicationResult.success(page);
        } catch (Exception e) {
            log.error("分页查询 AI 客户端 API 配置列表失败", e);
            return ApplicationResult.failure(ResponseCode.UN_ERROR, ResponseCode.UN_ERROR.getInfo(), null);
        }
    }

    public ApplicationResult<List<AiClientApiConfig>> queryAll() {
        try {
            return ApplicationResult.success(repository.findAll());
        } catch (Exception e) {
            log.error("查询所有 AI 客户端 API 配置失败", e);
            return ApplicationResult.failure(ResponseCode.UN_ERROR, ResponseCode.UN_ERROR.getInfo(), null);
        }
    }

    private static AiClientApiConfig toConfig(ApiConfigCommand command) {
        return AiClientApiConfig.builder()
                .id(command.id())
                .apiId(command.apiId())
                .baseUrl(command.baseUrl())
                .apiKey(command.apiKey())
                .completionsPath(command.completionsPath())
                .embeddingsPath(command.embeddingsPath())
                .status(command.status())
                .build();
    }

    private void assertApiIdAvailable(ApiConfigCommand command) {
        if (command != null && StringUtils.isNotBlank(command.apiId())
                && repository.findByApiId(command.apiId()).isPresent()) {
            throw new IllegalArgumentException("API ID 已存在: " + command.apiId());
        }
    }
}
