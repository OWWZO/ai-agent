package org.wwz.ai.infrastructure.llm.springai;

import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.runtime.llm.LlmRequest;
import org.wwz.ai.domain.agent.runtime.llm.LLMSettings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将供应商无关请求映射为 Spring AI OpenAI options。
 */
@Component
public class SpringAiOptionsMapper {

    private final SpringAiToolCallbackProvider toolCallbackProvider;

    public SpringAiOptionsMapper(SpringAiToolCallbackProvider toolCallbackProvider) {
        this.toolCallbackProvider = toolCallbackProvider;
    }

    public OpenAiChatOptions map(LlmRequest request) {
        LLMSettings settings = request.getSettings();
        if (settings == null) {
            throw new IllegalArgumentException("LLMSettings must not be null");
        }
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
                .model(request.getModel())
                .maxTokens(settings.getMaxTokens())
                .temperature(request.getTemperature() != null ? request.getTemperature() : settings.getTemperature());
        if (StringUtils.isNotBlank(settings.getReasoningEffort())) {
            builder.reasoningEffort(settings.getReasoningEffort());
        }
        applyExtParams(builder, settings.getExtParams());

        List<ToolCallback> callbacks = toolCallbackProvider.buildToolCallbacks(
                request.getTools(), request.getSessionId());
        if (!callbacks.isEmpty()) {
            builder.toolCallbacks(callbacks);
            if (StringUtils.isNotBlank(request.getToolChoice())) {
                builder.toolChoice(request.getToolChoice());
            }
        }
        builder.internalToolExecutionEnabled(false);
        return builder.build();
    }

    private void applyExtParams(OpenAiChatOptions.Builder builder, Map<String, Object> extParams) {
        if (extParams == null || extParams.isEmpty()) {
            return;
        }
        Map<String, Object> extraBody = new LinkedHashMap<>(extParams);
        Double temperature = removeDouble(extraBody, "temperature");
        if (temperature != null) builder.temperature(temperature);
        Integer maxTokens = removeInteger(extraBody, "max_tokens", "maxTokens");
        if (maxTokens != null) builder.maxTokens(maxTokens);
        Integer maxCompletionTokens = removeInteger(extraBody, "max_completion_tokens", "maxCompletionTokens");
        if (maxCompletionTokens != null) builder.maxCompletionTokens(maxCompletionTokens);
        Double topP = removeDouble(extraBody, "top_p", "topP");
        if (topP != null) builder.topP(topP);
        Double frequencyPenalty = removeDouble(extraBody, "frequency_penalty", "frequencyPenalty");
        if (frequencyPenalty != null) builder.frequencyPenalty(frequencyPenalty);
        Double presencePenalty = removeDouble(extraBody, "presence_penalty", "presencePenalty");
        if (presencePenalty != null) builder.presencePenalty(presencePenalty);
        Boolean parallelToolCalls = removeBoolean(extraBody, "parallel_tool_calls", "parallelToolCalls");
        if (parallelToolCalls != null) builder.parallelToolCalls(parallelToolCalls);
        Integer seed = removeInteger(extraBody, "seed");
        if (seed != null) builder.seed(seed);
        String reasoningEffort = removeString(extraBody, "reasoning_effort", "reasoningEffort");
        if (StringUtils.isNotBlank(reasoningEffort)) builder.reasoningEffort(reasoningEffort);
        String verbosity = removeString(extraBody, "verbosity");
        if (StringUtils.isNotBlank(verbosity)) builder.verbosity(verbosity);
        String serviceTier = removeString(extraBody, "service_tier", "serviceTier");
        if (StringUtils.isNotBlank(serviceTier)) builder.serviceTier(serviceTier);
        String user = removeString(extraBody, "user");
        if (StringUtils.isNotBlank(user)) builder.user(user);
        Boolean store = removeBoolean(extraBody, "store");
        if (store != null) builder.store(store);
        List<String> stop = removeStringList(extraBody, "stop", "stop_sequences", "stopSequences");
        if (!stop.isEmpty()) builder.stop(stop);
        if (!extraBody.isEmpty()) builder.extraBody(extraBody);
    }

    private Double removeDouble(Map<String, Object> source, String... keys) {
        Object value = removeFirst(source, keys);
        if (value instanceof Number number) return number.doubleValue();
        if (value instanceof String text && StringUtils.isNotBlank(text)) return Double.parseDouble(text.trim());
        return null;
    }

    private Integer removeInteger(Map<String, Object> source, String... keys) {
        Object value = removeFirst(source, keys);
        if (value instanceof Number number) return number.intValue();
        if (value instanceof String text && StringUtils.isNotBlank(text)) return Integer.parseInt(text.trim());
        return null;
    }

    private Boolean removeBoolean(Map<String, Object> source, String... keys) {
        Object value = removeFirst(source, keys);
        if (value instanceof Boolean bool) return bool;
        if (value instanceof String text && StringUtils.isNotBlank(text)) return Boolean.parseBoolean(text.trim());
        return null;
    }

    private String removeString(Map<String, Object> source, String... keys) {
        Object value = removeFirst(source, keys);
        return value == null ? null : String.valueOf(value);
    }

    private List<String> removeStringList(Map<String, Object> source, String... keys) {
        Object value = removeFirst(source, keys);
        if (value == null) return List.of();
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) if (item != null) result.add(String.valueOf(item));
            return result;
        }
        return value instanceof String text && StringUtils.isNotBlank(text) ? List.of(text) : List.of();
    }

    private Object removeFirst(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            if (source.containsKey(key)) return source.remove(key);
        }
        return null;
    }
}
