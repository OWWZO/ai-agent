package org.wwz.ai.application.catalog.model;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.wwz.ai.domain.agent.catalog.model.AiClientApiConfig;
import org.wwz.ai.domain.agent.catalog.model.AiClientModelConfig;
import org.wwz.ai.domain.agent.catalog.port.IAiClientApiConfigRepository;
import org.wwz.ai.domain.agent.catalog.port.IAiClientModelConfigRepository;
import org.wwz.ai.domain.agent.catalog.port.ICatalogModelRuntimePort;

import java.time.LocalDateTime;

/**
 * 模型目录创建所需的 API + Model 双表事务边界。
 */
@Service
@RequiredArgsConstructor
public class CatalogConfigurationApplicationService {

    private final IAiClientApiConfigRepository apiRepository;
    private final IAiClientModelConfigRepository modelRepository;
    private final ICatalogModelRuntimePort modelRuntimePort;

    @Transactional(rollbackFor = Exception.class)
    public boolean createModel(CatalogModelCreateCommand command) {
        String baseUrl = requireText(command == null ? null : command.baseUrl(), "Base URL 不能为空");
        String apiKey = requireText(command == null ? null : command.apiKey(), "API Key 不能为空");
        String modelId = requireText(command == null ? null : command.modelId(), "模型 ID 不能为空");
        String modelName = requireText(command == null ? null : command.modelName(), "上游模型名不能为空");
        assertModelIdAvailable(modelId);
        String apiId = resolveApiId(command.apiId(), modelId);
        LocalDateTime now = LocalDateTime.now();

        AiClientApiConfig api = AiClientApiConfig.builder()
                .apiId(apiId)
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .completionsPath(defaultIfBlank(command.completionsPath(), "/chat/completions"))
                .embeddingsPath(defaultIfBlank(command.embeddingsPath(), "/embeddings"))
                .status(normalizeStatus(command.status()))
                .createTime(now)
                .updateTime(now)
                .build();
        AiClientModelConfig model = AiClientModelConfig.builder()
                .modelId(modelId)
                .apiId(apiId)
                .modelName(modelName)
                .modelType(defaultIfBlank(command.modelType(), "openai"))
                .modelUsage(defaultIfBlank(command.modelUsage(), "default"))
                .supportsThinking(command.supportsThinking() == null ? 0 : command.supportsThinking())
                .contextWindow(command.contextWindow())
                .status(normalizeStatus(command.status()))
                .createTime(now)
                .updateTime(now)
                .build();

        if (!apiRepository.insert(api)) {
            throw new IllegalStateException("模型配置写入失败");
        }
        if (!modelRepository.insert(model)) {
            throw new IllegalStateException("模型配置写入失败");
        }
        modelRuntimePort.invalidateAll();
        return true;
    }

    /**
     * 业务 ID 由调用方负责选择；重复时显式失败，不能改写成随机 ID。
     */
    private String resolveApiId(String requestedApiId, String modelId) {
        String apiId = defaultIfBlank(requestedApiId, "api-" + modelId);
        if (apiRepository.findByApiId(apiId).isPresent()) {
            throw new IllegalArgumentException("API ID 已存在: " + apiId);
        }
        return apiId;
    }

    private void assertModelIdAvailable(String modelId) {
        java.util.List<AiClientModelConfig> existing = modelRepository.findAllByModelId(modelId);
        if (existing != null && !existing.isEmpty()) {
            throw new IllegalArgumentException("模型 ID 已存在: " + modelId);
        }
    }

    private static int normalizeStatus(Integer status) {
        return status != null && status == 0 ? 0 : 1;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
