package org.wwz.ai.domain.agent.reactor.agent.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.wwz.ai.domain.agent.reactor.agent.util.SpringContextHolder;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;

import java.util.Objects;


/**
 * 配置工具类。
 * 默认配置从 Spring Environment 读取，支持 profile（如 application-dev.yml 中的 llm.default.*）。
 */
@Slf4j
public class Config {
    /**
     * 获取 LLM 配置
     */
    public static LLMSettings getLLMConfig(String modelName) {
        ApplicationContext applicationContext = SpringContextHolder.getApplicationContext();
        ReactorConfig reactorConfig = applicationContext.getBean(ReactorConfig.class);
        if (Objects.nonNull(reactorConfig.getLlmSettingsMap())) {
            return reactorConfig.getLlmSettingsMap().getOrDefault(modelName, getDefaultConfig(applicationContext));
        }
        return getDefaultConfig(applicationContext);
    }

    /**
     * 从 Spring Environment 加载默认 LLM 配置（会使用当前激活的 profile，如 dev）。
     */
    private static LLMSettings getDefaultConfig(ApplicationContext applicationContext) {
        Environment env = applicationContext.getEnvironment();
        return LLMSettings.builder()
                .model(env.getProperty("llm.default.model", "gpt-4o-0806"))
                .maxTokens(parseInt(env.getProperty("llm.default.max_tokens"), 16384))
                .temperature(parseDouble(env.getProperty("llm.default.temperature"), 0.0))
                .baseUrl(env.getProperty("llm.default.base_url", ""))
                .interfaceUrl(env.getProperty("llm.default.interface_url", "/v1/chat/completions"))
                .functionCallType(env.getProperty("llm.default.function_call_type", "function_call"))
                .apiKey(env.getProperty("llm.default.apikey", ""))
                .maxInputTokens(parseInt(env.getProperty("llm.default.max_input_tokens"), 100000))
                .build();
    }

    private static int parseInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static double parseDouble(String value, double defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}