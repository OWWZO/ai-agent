package org.wwz.ai.infrastructure.adapter.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;
import org.wwz.ai.domain.agent.adapter.repository.ILlmModelConfigRepository;
import org.wwz.ai.domain.agent.runtime.llm.LlmModelBinding;
import org.wwz.ai.infrastructure.dao.IAiClientApiDao;
import org.wwz.ai.infrastructure.dao.IAiClientModelDao;
import org.wwz.ai.infrastructure.dao.po.AiClientApi;
import org.wwz.ai.infrastructure.dao.po.AiClientModel;

import java.util.ArrayList;
import java.util.List;

/**
 * 从 {@code ai_client_model} + {@code ai_client_api} 组装可出站模型绑定。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class LlmModelConfigRepository implements ILlmModelConfigRepository {

    private final IAiClientModelDao aiClientModelDao;
    private final IAiClientApiDao aiClientApiDao;

    @Override
    public List<LlmModelBinding> listUsable() {
        List<AiClientModel> models = aiClientModelDao.queryEnabledModels();
        if (models == null || models.isEmpty()) {
            return List.of();
        }
        List<LlmModelBinding> result = new ArrayList<>();
        for (AiClientModel model : models) {
            if (model == null || !StringUtils.hasText(model.getModelId()) || !StringUtils.hasText(model.getModelName())) {
                continue;
            }
            if (!StringUtils.hasText(model.getApiId())) {
                continue;
            }
            AiClientApi api = aiClientApiDao.queryByApiId(model.getApiId());
            if (api == null || !isEnabled(api.getStatus())) {
                continue;
            }
            if (!StringUtils.hasText(api.getBaseUrl()) || !StringUtils.hasText(api.getApiKey())) {
                log.debug("跳过模型 {}：API {} 缺少 baseUrl 或 apiKey", model.getModelId(), model.getApiId());
                continue;
            }
            result.add(LlmModelBinding.builder()
                    .modelId(model.getModelId().trim())
                    .modelName(model.getModelName().trim())
                    .apiId(model.getApiId().trim())
                    .modelUsage(model.getModelUsage())
                    .baseUrl(api.getBaseUrl().trim())
                    .apiKey(api.getApiKey().trim())
                    .completionsPath(StringUtils.hasText(api.getCompletionsPath())
                            ? api.getCompletionsPath().trim()
                            : null)
                    .contextWindow(model.getContextWindow())
                    .build());
        }
        return result;
    }

    private static boolean isEnabled(Integer status) {
        return status != null && status == 1;
    }
}
