package org.wwz.ai.domain.agent.runtime.llm;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * todo:后续实现前端拖拉编排组装agent模型配置
 * 基于 LLMSettings 解析并缓存 OpenAiChatModel。
 * 第一阶段优先复用 Reactor 既有 llm.settings，避免强依赖 Armory modelId 装配。
 */
@Slf4j
@Component
public class LlmChatModelResolver {

    private final Map<String, OpenAiChatModel> modelCache = new ConcurrentHashMap<>();

    /**
     * 解析可复用的 OpenAiChatModel。
     */
    public OpenAiChatModel resolve(LLMSettings settings) {
        if (settings == null) {
            throw new IllegalArgumentException("LLMSettings must not be null");
        }
        String cacheKey = buildCacheKey(settings);
        return modelCache.computeIfAbsent(cacheKey, key -> createModel(settings));
    }

    private String buildCacheKey(LLMSettings settings) {
        return String.join("::",
                StringUtils.defaultString(settings.getModel()),
                StringUtils.defaultString(settings.getBaseUrl()),
                StringUtils.defaultString(settings.getInterfaceUrl()),
                StringUtils.defaultString(settings.getApiKey()),
                JSON.toJSONString(settings.getExtParams()));
    }

    /**
     * 使用当前 LLMSettings 构造 OpenAI-compatible ChatModel。
     */
    private OpenAiChatModel createModel(LLMSettings settings) {
        String baseUrl = StringUtils.trimToEmpty(settings.getBaseUrl());
        if (StringUtils.isBlank(baseUrl)) {
            throw new IllegalArgumentException("Base URL is not configured for model: " + settings.getModel());
        }

        String completionsPath = StringUtils.isNotBlank(settings.getInterfaceUrl())
                ? settings.getInterfaceUrl().trim()
                : "/v1/chat/completions";

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(StringUtils.defaultString(settings.getApiKey()))
                .completionsPath(completionsPath)
                .build();

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(settings.getModel())
                        .build())
                .build();

        log.info("初始化 Spring AI ChatModel: model={}, baseUrl={}, completionsPath={}",
                settings.getModel(), baseUrl, completionsPath);
        return chatModel;
    }
}
