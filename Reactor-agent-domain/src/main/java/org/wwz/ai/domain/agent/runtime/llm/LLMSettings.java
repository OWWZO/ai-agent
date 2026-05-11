package org.wwz.ai.domain.agent.runtime.llm;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * LLM 配置类。
 * 与 application-dev.yml 中 llm.settings 的 JSON 键一致：使用 snake_case（base_url 等），通过 @JSONField 映射到 camelCase 字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LLMSettings {
    private String model;

    @JSONField(name = "max_tokens")
    private int maxTokens;

    private double temperature;

    private String apiType;

    @JSONField(name = "apikey")
    private String apiKey;

    private String apiVersion;

    @JSONField(name = "base_url")
    private String baseUrl;

    @JSONField(name = "interface_url")
    private String interfaceUrl;

    private String functionCallType;

    @JSONField(name = "max_input_tokens")
    private int maxInputTokens;

    private Map<String, Object> extParams;
}