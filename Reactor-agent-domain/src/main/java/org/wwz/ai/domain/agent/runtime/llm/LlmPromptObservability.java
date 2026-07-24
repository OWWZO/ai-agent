package org.wwz.ai.domain.agent.runtime.llm;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LLM 请求观测：日志 + 可落库快照（ai_agent_llm_invocation）。
 */
@Slf4j
public final class LlmPromptObservability {

    private static final int MAX_CONTENT_CHARS = 8000;
    private static final TokenCounter TOKEN_COUNTER = new TokenCounter();
    private static final ConcurrentHashMap<String, PromptSnapshot> LAST_BY_SESSION = new ConcurrentHashMap<>();
    private static final ThreadLocal<ObservationBundle> LAST_BUNDLE = new ThreadLocal<>();

    private LlmPromptObservability() {
    }

    public static ObservationBundle logRequest(AgentContext context,
                                               String model,
                                               String callKind,
                                               Message systemMessage,
                                               List<Message> messages,
                                               ToolCollection tools) {
        TokenCounter.PromptEstimate estimate = TOKEN_COUNTER.estimatePrompt(systemMessage, messages, tools);
        String requestId = context == null ? "-" : StringUtils.defaultString(context.getRequestId(), "-");
        String sessionId = context == null ? "-" : StringUtils.defaultString(context.getSessionId(), "-");
        String roleSeq = TOKEN_COUNTER.summarizeRoles(messages);

        String reqLine = "[LLM-REQ] kind=" + callKind + " model=" + model + " session=" + sessionId + " " + estimate.toLogLine();
        String roleLine = "[LLM-REQ] roleSeq=[" + roleSeq + "] messageCount=" + estimate.getMessageCount();
        log.info("{} {}", requestId, reqLine);
        log.info("{} {}", requestId, roleLine);

        PromptSnapshot prev = LAST_BY_SESSION.get(sessionId);
        PromptSnapshot curr = new PromptSnapshot(
                estimate.getSystemFingerprint(),
                String.join(",", estimate.getToolNames() == null ? List.of() : estimate.getToolNames()),
                estimate.getMessageCount(),
                estimate.getSystemChars()
        );

        String cacheStatus = "UNKNOWN";
        String cacheRiskFlags = null;
        String cacheLine;
        if (prev != null && !"-".equals(sessionId)) {
            boolean systemChanged = !Objects.equals(prev.systemFingerprint(), curr.systemFingerprint());
            boolean toolsChanged = !Objects.equals(prev.toolNamesKey(), curr.toolNamesKey());
            boolean messagesShrunk = curr.messageCount() + 1 < prev.messageCount();
            if (systemChanged || toolsChanged || messagesShrunk) {
                cacheStatus = "RISK";
                List<String> flags = new ArrayList<>();
                if (systemChanged) {
                    flags.add("systemChanged");
                }
                if (toolsChanged) {
                    flags.add("toolsChanged");
                }
                if (messagesShrunk) {
                    flags.add("messagesShrunk");
                }
                cacheRiskFlags = String.join(",", flags);
                cacheLine = "[PROMPT-CACHE-RISK] session=" + sessionId
                        + " systemChanged=" + systemChanged
                        + " toolsChanged=" + toolsChanged
                        + " messagesShrunk=" + messagesShrunk
                        + " prev(systemFp=" + prev.systemFingerprint()
                        + ",tools=" + prev.toolNamesKey()
                        + ",msgN=" + prev.messageCount()
                        + ") curr(systemFp=" + curr.systemFingerprint()
                        + ",tools=" + curr.toolNamesKey()
                        + ",msgN=" + curr.messageCount() + ")";
                log.warn("{} {}", requestId, cacheLine);
            } else {
                cacheStatus = "OK";
                cacheLine = "[PROMPT-CACHE-OK] session=" + sessionId
                        + " systemFp=" + curr.systemFingerprint()
                        + " tools=" + curr.toolNamesKey()
                        + " msgN=" + curr.messageCount();
                log.info("{} {}", requestId, cacheLine);
            }
        } else {
            cacheLine = "[PROMPT-CACHE-OK] session=" + sessionId + " firstSeen=true systemFp=" + curr.systemFingerprint();
            cacheStatus = "OK";
            log.info("{} {}", requestId, cacheLine);
        }
        if (!"-".equals(sessionId)) {
            LAST_BY_SESSION.put(sessionId, curr);
        }

        String promptPayloadJson = buildPromptPayloadJson(systemMessage, messages, tools, estimate, roleSeq);
        List<String> lines = new ArrayList<>();
        lines.add(reqLine);
        lines.add(roleLine);
        lines.add(cacheLine);

        ObservationBundle bundle = ObservationBundle.builder()
                .estimate(estimate)
                .promptPayloadJson(promptPayloadJson)
                .systemFingerprint(estimate.getSystemFingerprint())
                .roleSeq(roleSeq)
                .cacheStatus(cacheStatus)
                .cacheRiskFlags(cacheRiskFlags)
                .obsLines(lines)
                .build();
        LAST_BUNDLE.set(bundle);
        return bundle;
    }

    public static void logResponse(AgentContext context,
                                   String model,
                                   String callKind,
                                   Integer promptTokens,
                                   Integer completionTokens,
                                   Integer totalTokens,
                                   Integer cachedPromptTokens,
                                   long durationMs) {
        ObservationBundle bundle = LAST_BUNDLE.get();
        TokenCounter.PromptEstimate estimate = bundle == null ? null : bundle.getEstimate();
        String requestId = context == null ? "-" : StringUtils.defaultString(context.getRequestId(), "-");
        int est = estimate == null ? -1 : estimate.getEstimatedTotalTokens();
        Integer uncached = null;
        if (promptTokens != null && cachedPromptTokens != null) {
            uncached = Math.max(0, promptTokens - cachedPromptTokens);
        }
        double cacheHitRatio = -1;
        if (promptTokens != null && promptTokens > 0 && cachedPromptTokens != null) {
            cacheHitRatio = cachedPromptTokens * 1.0 / promptTokens;
        }
        String respLine = "[LLM-RESP] kind=" + callKind
                + " model=" + model
                + " durationMs=" + durationMs
                + " usage(prompt=" + promptTokens
                + ",completion=" + completionTokens
                + ",total=" + totalTokens
                + ",cachedPrompt=" + cachedPromptTokens
                + ",uncachedPrompt=" + uncached
                + ",cacheHitRatio=" + (cacheHitRatio < 0 ? "n/a" : String.format("%.2f", cacheHitRatio))
                + ") estPromptTotal~" + est
                + " estDelta=" + ((promptTokens == null || est < 0) ? "n/a" : (promptTokens - est));
        log.info("{} {}", requestId, respLine);

        String missLine = null;
        String cacheStatus = bundle == null ? "UNKNOWN" : bundle.getCacheStatus();
        String cacheRiskFlags = bundle == null ? null : bundle.getCacheRiskFlags();
        if (cachedPromptTokens != null && cachedPromptTokens == 0 && promptTokens != null && promptTokens > 500) {
            missLine = "[PROMPT-CACHE-MISS?] cachedPrompt=0 promptTokens=" + promptTokens;
            log.warn("{} {}", requestId, missLine);
            if (!"RISK".equals(cacheStatus)) {
                cacheStatus = "MISS";
            } else if (cacheRiskFlags == null || !cacheRiskFlags.contains("cachedZero")) {
                cacheRiskFlags = cacheRiskFlags == null ? "cachedZero" : cacheRiskFlags + ",cachedZero";
            }
        }

        if (bundle != null) {
            List<String> lines = new ArrayList<>(bundle.getObsLines() == null ? List.of() : bundle.getObsLines());
            lines.add(respLine);
            if (missLine != null) {
                lines.add(missLine);
            }
            Map<String, Object> obs = new LinkedHashMap<>();
            obs.put("lines", lines);
            obs.put("cacheStatus", cacheStatus);
            obs.put("cacheRiskFlags", cacheRiskFlags);
            obs.put("promptTokens", promptTokens);
            obs.put("completionTokens", completionTokens);
            obs.put("totalTokens", totalTokens);
            obs.put("cachedPromptTokens", cachedPromptTokens);
            obs.put("uncachedPromptTokens", uncached);
            obs.put("cacheHitRatio", cacheHitRatio < 0 ? null : cacheHitRatio);
            obs.put("estTotalTokens", estimate == null ? null : estimate.getEstimatedTotalTokens());
            obs.put("durationMs", durationMs);
            bundle.setCacheStatus(cacheStatus);
            bundle.setCacheRiskFlags(cacheRiskFlags);
            bundle.setCachedPromptTokens(cachedPromptTokens);
            bundle.setObsLogJson(JSON.toJSONString(obs, SerializerFeature.DisableCircularReferenceDetect));
            bundle.setObsLines(lines);
        }
        // keep bundle for finishLlmInvocation to read via current()
    }

    public static ObservationBundle current() {
        return LAST_BUNDLE.get();
    }

    public static void clear() {
        LAST_BUNDLE.remove();
    }

    public static Integer resolveCachedPromptTokens(ChatResponseMetadata metadata) {
        if (metadata == null) {
            return null;
        }
        Usage usage = metadata.getUsage();
        if (usage != null) {
            try {
                Integer fromNative = extractFromObject(usage.getNativeUsage());
                if (fromNative != null) {
                    return fromNative;
                }
            } catch (Exception ignored) {
            }
        }
        try {
            return extractFromObject(metadata.get("usage"));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String buildPromptPayloadJson(Message systemMessage,
                                                 List<Message> messages,
                                                 ToolCollection tools,
                                                 TokenCounter.PromptEstimate estimate,
                                                 String roleSeq) {
        // messages：与正式发送一致，但不含 SYSTEM（system 仅 meta.systemFingerprint）；tools = function schema
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("kind", "exact_wire_no_system");
        payload.put("messages", buildExactMessages(systemMessage, messages));
        payload.put("tools", buildExactTools(tools));
        // 辅助对照字段（非 wire body，便于查 cache）
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("roleSeq", roleSeq);
        meta.put("systemFingerprint", estimate == null ? null : estimate.getSystemFingerprint());
        meta.put("estimate", estimate);
        payload.put("meta", meta);
        return JSON.toJSONString(payload, SerializerFeature.DisableCircularReferenceDetect);
    }

    /**
     * 与 LLM.mergeMessages(system, messages) 顺序一致，字段按领域 Message 原样序列化（不截断）。
     */
    private static List<Map<String, Object>> buildExactMessages(Message systemMessage, List<Message> messages) {
        // 不落 SYSTEM 全文：高度稳定且体积大，指纹放 meta.systemFingerprint 即可对照
        List<Map<String, Object>> out = new ArrayList<>();
        if (messages != null) {
            for (Message message : messages) {
                if (message == null || message.getRole() == null) {
                    continue;
                }
                if (message.getRole() == org.wwz.ai.domain.agent.runtime.enums.RoleType.SYSTEM) {
                    continue;
                }
                out.add(toExactMessageMap(message));
            }
        }
        return out;
    }

    private static Map<String, Object> toExactMessageMap(Message message) {
        Map<String, Object> one = new LinkedHashMap<>();
        one.put("role", message.getRole() == null ? null : message.getRole().name());
        // content 原样，不 truncate
        one.put("content", message.getContent());
        if (message.getBase64Image() != null) {
            one.put("base64Image", message.getBase64Image());
        }
        if (StringUtils.isNotBlank(message.getToolCallId())) {
            one.put("toolCallId", message.getToolCallId());
        }
        if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
            List<Map<String, Object>> tcs = new ArrayList<>();
            for (ToolCall tc : message.getToolCalls()) {
                if (tc == null) {
                    continue;
                }
                Map<String, Object> t = new LinkedHashMap<>();
                t.put("id", tc.getId());
                t.put("type", tc.getType());
                if (tc.getFunction() != null) {
                    Map<String, Object> fn = new LinkedHashMap<>();
                    fn.put("name", tc.getFunction().getName());
                    fn.put("arguments", tc.getFunction().getArguments());
                    t.put("function", fn);
                }
                tcs.add(t);
            }
            one.put("toolCalls", tcs);
        }
        return one;
    }

    /**
     * 与 OpenAI/Spring AI 工具声明一致：name + description + parameters schema。
     */
    private static List<Map<String, Object>> buildExactTools(ToolCollection tools) {
        List<Map<String, Object>> formatted = new ArrayList<>();
        if (tools == null) {
            return formatted;
        }
        // 与 LlmToolCallbackProvider 一致：name 字典序 + 稳定 schema
        if (tools.getToolMap() != null) {
            tools.getToolMap().values().stream()
                    .filter(tool -> tool != null && StringUtils.isNotBlank(tool.getName()))
                    .sorted(java.util.Comparator.comparing(
                            org.wwz.ai.domain.agent.runtime.tool.BaseTool::getName,
                            String.CASE_INSENSITIVE_ORDER))
                    .forEach(tool -> {
                        Map<String, Object> function = new LinkedHashMap<>();
                        function.put("name", tool.getName());
                        function.put("description", tool.getDescription());
                        try {
                            function.put("parameters",
                                    org.wwz.ai.domain.agent.runtime.util.ToolSchemaNormalizer.normalizeSchema(
                                            tool.toParams(), tool.getName()));
                            // 再做 key 排序，保证 JSON 字节稳定
                            function.put("parameters",
                                    org.wwz.ai.domain.agent.runtime.util.ToolSchemaNormalizer.sortDeep(
                                            function.get("parameters")));
                        } catch (Exception e) {
                            function.put("parameters", tool.toParams());
                            function.put("parametersError", e.getMessage());
                        }
                        Map<String, Object> toolMap = new LinkedHashMap<>();
                        toolMap.put("type", "function");
                        toolMap.put("function", function);
                        formatted.add(toolMap);
                    });
        }
        if (tools.getMcpToolMap() != null) {
            tools.getMcpToolMap().values().stream()
                    .filter(tool -> tool != null && StringUtils.isNotBlank(tool.getName()))
                    .sorted(java.util.Comparator.comparing(
                            org.wwz.ai.domain.agent.runtime.dto.tool.McpToolInfo::getName,
                            String.CASE_INSENSITIVE_ORDER))
                    .forEach(tool -> {
                        Map<String, Object> function = new LinkedHashMap<>();
                        function.put("name", tool.getName());
                        function.put("description", tool.getDesc());
                        Object parameters = tool.getParameters();
                        if (parameters instanceof String s && StringUtils.isNotBlank(s)) {
                            try {
                                parameters = JSON.parse(s);
                            } catch (Exception ignored) {
                            }
                        }
                        if (parameters instanceof Map<?, ?>) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> map = (Map<String, Object>) parameters;
                            parameters = org.wwz.ai.domain.agent.runtime.util.ToolSchemaNormalizer.normalizeSchema(
                                    map, tool.getName());
                            parameters = org.wwz.ai.domain.agent.runtime.util.ToolSchemaNormalizer.sortDeep(parameters);
                        }
                        function.put("parameters", parameters);
                        Map<String, Object> toolMap = new LinkedHashMap<>();
                        toolMap.put("type", "function");
                        toolMap.put("function", function);
                        formatted.add(toolMap);
                    });
        }
        return formatted;
    }




    private static String truncate(String text) {
        if (text == null) {
            return null;
        }
        if (text.length() <= MAX_CONTENT_CHARS) {
            return text;
        }
        return text.substring(0, MAX_CONTENT_CHARS) + "...(truncated,total=" + text.length() + ")";
    }

    @SuppressWarnings("unchecked")
    private static Integer extractFromObject(Object nativeUsage) {
        if (!(nativeUsage instanceof Map<?, ?> map)) {
            return null;
        }
        Object details = map.get("prompt_tokens_details");
        if (details instanceof Map<?, ?> d) {
            Object cached = d.get("cached_tokens");
            if (cached instanceof Number n) {
                return n.intValue();
            }
        }
        Object cached = map.get("cached_tokens");
        if (cached instanceof Number n) {
            return n.intValue();
        }
        Object cacheRead = map.get("cache_read_input_tokens");
        if (cacheRead instanceof Number n) {
            return n.intValue();
        }
        return null;
    }

    private record PromptSnapshot(String systemFingerprint, String toolNamesKey, int messageCount, int systemChars) {
    }

    @Data
    @Builder
    public static class ObservationBundle {
        private TokenCounter.PromptEstimate estimate;
        private String promptPayloadJson;
        private String systemFingerprint;
        private String roleSeq;
        private String cacheStatus;
        private String cacheRiskFlags;
        private Integer cachedPromptTokens;
        private String obsLogJson;
        private List<String> obsLines;
    }
}
