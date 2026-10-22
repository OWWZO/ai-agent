package org.wwz.ai.infrastructure.llm.springai;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.ai.tool.ToolCallback;
import org.wwz.ai.domain.agent.runtime.llm.LlmToolDefinition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 构建并缓存 Spring AI 工具回调列表，保持原有会话级工具顺序和复用策略。
 */
@Component
public class SpringAiToolCallbackProvider {

    private static final int MAX_SESSION_CACHE = 256;
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, List<ToolCallback>>> sessionCache =
            new ConcurrentHashMap<>();

    public List<ToolCallback> buildToolCallbacks(List<LlmToolDefinition> definitions, String sessionId) {
        if (definitions == null || definitions.isEmpty()) {
            return List.of();
        }
        String signature = signature(definitions);
        if (StringUtils.isBlank(sessionId)) {
            return buildSorted(definitions);
        }
        ConcurrentHashMap<String, List<ToolCallback>> bySignature =
                sessionCache.computeIfAbsent(sessionId, ignored -> new ConcurrentHashMap<>());
        List<ToolCallback> cached = bySignature.get(signature);
        if (cached != null) {
            return cached;
        }
        List<ToolCallback> frozen = List.copyOf(buildSorted(definitions));
        bySignature.put(signature, frozen);
        trimSessionCache();
        return frozen;
    }

    private List<ToolCallback> buildSorted(List<LlmToolDefinition> definitions) {
        return definitions.stream()
                .filter(definition -> definition != null && StringUtils.isNotBlank(definition.getName()))
                .sorted(Comparator.comparing(LlmToolDefinition::getName, String.CASE_INSENSITIVE_ORDER))
                .map(SpringAiToolCallbackAdapter::new)
                .collect(Collectors.toList());
    }

    private String signature(List<LlmToolDefinition> definitions) {
        List<String> names = new ArrayList<>();
        for (LlmToolDefinition definition : definitions) {
            if (definition != null && StringUtils.isNotBlank(definition.getName())) {
                names.add(definition.getName());
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return String.join("|", names);
    }

    private void trimSessionCache() {
        if (sessionCache.size() <= MAX_SESSION_CACHE) {
            return;
        }
        int remove = sessionCache.size() / 2;
        for (String key : sessionCache.keySet()) {
            sessionCache.remove(key);
            if (--remove <= 0) {
                break;
            }
        }
    }
}
