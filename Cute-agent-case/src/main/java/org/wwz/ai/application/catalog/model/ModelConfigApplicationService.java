package org.wwz.ai.application.catalog.model;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.application.catalog.ApplicationResult;
import org.wwz.ai.application.catalog.model.ModelConfigCommand;
import org.wwz.ai.application.catalog.model.ModelConfigQueryCommand;
import org.wwz.ai.domain.agent.catalog.model.AiClientModelConfig;
import org.wwz.ai.domain.agent.catalog.model.ModelConnectionTestResult;
import org.wwz.ai.domain.agent.catalog.port.IAiClientModelConfigRepository;
import org.wwz.ai.domain.agent.catalog.port.ICatalogModelRuntimePort;
import org.wwz.ai.domain.agent.catalog.port.IModelConnectionTestPort;
import org.wwz.ai.types.enums.ResponseCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 模型配置管理用例。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelConfigApplicationService {

    private final IAiClientModelConfigRepository repository;
    private final ICatalogModelRuntimePort modelRuntimePort;
    private final IModelConnectionTestPort connectionTestPort;

    public ApplicationResult<ModelConnectionTestResult> testConnection(String modelReference) {
        return ApplicationResult.success(connectionTestPort.test(modelReference));
    }

    public ApplicationResult<ModelConnectionTestResult> testConnectionById(Long id) {
        return ApplicationResult.success(connectionTestPort.testByRecordId(id));
    }

    public ApplicationResult<Boolean> create(ModelConfigCommand command) {
        try {
            assertModelIdAvailable(command);
            AiClientModelConfig config = toConfig(command);
            LocalDateTime now = LocalDateTime.now();
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
            log.error("创建 AI 客户端模型配置失败", e);
            return ApplicationResult.failure(ResponseCode.UN_ERROR, ResponseCode.UN_ERROR.getInfo(), false);
        }
    }

    public ApplicationResult<Boolean> updateById(ModelConfigCommand command) {
        if (command == null || command.id() == null) {
            return ApplicationResult.failure(ResponseCode.ILLEGAL_PARAMETER, "ID不能为空", false);
        }
        try {
            AiClientModelConfig config = toConfig(command);
            config.setUpdateTime(LocalDateTime.now());
            boolean changed = repository.updateById(config);
            if (changed) {
                modelRuntimePort.invalidateAll();
            }
            return ApplicationResult.success(changed);
        } catch (Exception e) {
            log.error("根据 ID 更新 AI 客户端模型配置失败", e);
            return ApplicationResult.failure(ResponseCode.UN_ERROR, ResponseCode.UN_ERROR.getInfo(), false);
        }
    }

    public ApplicationResult<Boolean> updateByModelId(ModelConfigCommand command) {
        if (command == null || !StringUtils.isNotBlank(command.modelId())) {
            return ApplicationResult.failure(ResponseCode.ILLEGAL_PARAMETER, "模型ID不能为空", false);
        }
        if (command.id() != null) {
            return updateById(command);
        }
        try {
            if (repository.findAllByModelId(command.modelId()).size() != 1) {
                return ApplicationResult.failure(
                        ResponseCode.ILLEGAL_PARAMETER, "模型ID存在多条配置，请使用记录ID更新", false);
            }
            AiClientModelConfig config = toConfig(command);
            config.setUpdateTime(LocalDateTime.now());
            boolean changed = repository.updateByModelId(config);
            if (changed) {
                modelRuntimePort.invalidateAll();
            }
            return ApplicationResult.success(changed);
        } catch (Exception e) {
            log.error("根据模型 ID 更新 AI 客户端模型配置失败", e);
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
            log.error("根据 ID 删除 AI 客户端模型配置失败", e);
            return ApplicationResult.failure(ResponseCode.UN_ERROR, ResponseCode.UN_ERROR.getInfo(), false);
        }
    }

    public ApplicationResult<Boolean> deleteByModelId(String modelId) {
        try {
            if (repository.findAllByModelId(modelId).size() != 1) {
                return ApplicationResult.failure(
                        ResponseCode.ILLEGAL_PARAMETER, "模型ID存在多条配置，请使用记录ID删除", false);
            }
            boolean changed = repository.deleteByModelId(modelId);
            if (changed) {
                modelRuntimePort.invalidateAll();
            }
            return ApplicationResult.success(changed);
        } catch (Exception e) {
            log.error("根据模型 ID 删除 AI 客户端模型配置失败", e);
            return ApplicationResult.failure(ResponseCode.UN_ERROR, ResponseCode.UN_ERROR.getInfo(), false);
        }
    }

    public ApplicationResult<AiClientModelConfig> queryById(Long id) {
        try {
            return repository.findById(id)
                    .map(ApplicationResult::success)
                    .orElseGet(() -> ApplicationResult.failure(
                            ResponseCode.UN_ERROR, "未找到对应的AI客户端模型配置", null));
        } catch (Exception e) {
            log.error("根据 ID 查询 AI 客户端模型配置失败", e);
            return ApplicationResult.failure(ResponseCode.UN_ERROR, ResponseCode.UN_ERROR.getInfo(), null);
        }
    }

    public ApplicationResult<AiClientModelConfig> queryByModelId(String modelId) {
        try {
            return repository.findByModelId(modelId)
                    .map(ApplicationResult::success)
                    .orElseGet(() -> ApplicationResult.failure(
                            ResponseCode.UN_ERROR, "未找到对应的AI客户端模型配置", null));
        } catch (Exception e) {
            log.error("根据模型 ID 查询 AI 客户端模型配置失败", e);
            return ApplicationResult.failure(ResponseCode.UN_ERROR, ResponseCode.UN_ERROR.getInfo(), null);
        }
    }

    public ApplicationResult<List<AiClientModelConfig>> queryByApiId(String apiId) {
        return queryList(() -> repository.findByApiId(apiId), "根据 API 配置 ID 查询 AI 客户端模型配置列表失败");
    }

    public ApplicationResult<List<AiClientModelConfig>> queryByModelType(String modelType) {
        return queryList(() -> repository.findByModelType(modelType), "根据模型类型查询 AI 客户端模型配置列表失败");
    }

    public ApplicationResult<List<AiClientModelConfig>> queryEnabled() {
        return queryList(repository::findEnabled, "查询所有启用的 AI 客户端模型配置失败");
    }

    public ApplicationResult<List<AiClientModelConfig>> queryList(ModelConfigQueryCommand command) {
        try {
            List<AiClientModelConfig> models;
            if (StringUtils.isNotBlank(command.modelId())) {
                models = repository.findAllByModelId(command.modelId());
            } else if (StringUtils.isNotBlank(command.apiId())) {
                models = repository.findByApiId(command.apiId());
            } else if (StringUtils.isNotBlank(command.modelType())) {
                models = repository.findByModelType(command.modelType());
            } else if (command.status() != null) {
                models = command.status() == 1 ? repository.findEnabled() : repository.findAll();
            } else {
                models = repository.findAll();
            }
            return ApplicationResult.success(models);
        } catch (Exception e) {
            log.error("根据条件查询 AI 客户端模型配置列表失败", e);
            return ApplicationResult.failure(ResponseCode.UN_ERROR, ResponseCode.UN_ERROR.getInfo(), null);
        }
    }

    public ApplicationResult<List<AiClientModelConfig>> queryAll() {
        return queryList(repository::findAll, "查询所有 AI 客户端模型配置失败");
    }

    private ApplicationResult<List<AiClientModelConfig>> queryList(
            java.util.function.Supplier<List<AiClientModelConfig>> query,
            String errorMessage) {
        try {
            return ApplicationResult.success(query.get());
        } catch (Exception e) {
            log.error(errorMessage, e);
            return ApplicationResult.failure(ResponseCode.UN_ERROR, ResponseCode.UN_ERROR.getInfo(), null);
        }
    }

    private static AiClientModelConfig toConfig(ModelConfigCommand command) {
        return AiClientModelConfig.builder()
                .id(command.id())
                .modelId(command.modelId())
                .apiId(command.apiId())
                .modelName(command.modelName())
                .modelType(command.modelType())
                .modelUsage(command.modelUsage())
                .supportsThinking(command.supportsThinking())
                .contextWindow(command.contextWindow())
                .status(command.status())
                .build();
    }

    private void assertModelIdAvailable(ModelConfigCommand command) {
        if (command == null || !StringUtils.isNotBlank(command.modelId())) {
            return;
        }
        List<AiClientModelConfig> existing = repository.findAllByModelId(command.modelId());
        if (existing != null && !existing.isEmpty()) {
            throw new IllegalArgumentException("模型 ID 已存在: " + command.modelId());
        }
    }
}
