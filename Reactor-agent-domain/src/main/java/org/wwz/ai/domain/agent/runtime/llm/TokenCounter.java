package org.wwz.ai.domain.agent.runtime.llm;

import com.alibaba.fastjson.JSON;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.dto.tool.McpToolInfo;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.runtime.enums.RoleType;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.util.ToolSchemaNormalizer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Token 粗估器（默认按约 4 chars/token 估算）。
 * <p>
 * 说明：{@link #countText(String)} 仍返回<strong>字符数</strong>（兼容 history 预算等旧调用）。
 * 真正 token 粗估请用 {@link #estimateTokens(String)} / {@link #estimatePrompt}。
 */
public class TokenCounter {

    /** 每 token 默认按 4 字节估算。 */
    public static final int CHARS_PER_TOKEN = 4;
    public static final String SOURCE_LOCAL_ESTIMATE = "local_estimate";
    public static final String SOURCE_PROVIDER_USAGE = "provider_usage";
    private static final int BASE_MESSAGE_TOKENS = 4;
    private static final int FORMAT_TOKENS = 2;
    public static final int IMAGE_DEFAULT_TOKENS = 2000;

    public TokenCounter() {
    }

    /**
     * 兼容旧语义：返回字符数（不是 token）。
     */
    public int countText(String text) {
        return text == null ? 0 : text.length();
    }

    /**
     * 粗估 token：ceil(chars / 4)。
     */
    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (text.length() + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN;
    }

    public int countImage(Map<String, Object> imageItem) {
        if (imageItem == null) {
            return IMAGE_DEFAULT_TOKENS;
        }
        return IMAGE_DEFAULT_TOKENS;
    }

    public int countContent(Object content) {
        if (content == null) {
            return 0;
        }
        if (content instanceof String text) {
            if (looksLikeMediaPayload(text)) {
                return IMAGE_DEFAULT_TOKENS;
            }
            return estimateTokens(text);
        }
        if (content instanceof List) {
            int tokenCount = 0;
            for (Object item : (List<?>) content) {
                tokenCount += countContentItem(item);
            }
            return tokenCount;
        }
        if (content instanceof Map<?, ?> map) {
            return countContentItem(map);
        }
        return estimateTokens(String.valueOf(content));
    }

    @SuppressWarnings("unchecked")
    private int countContentItem(Object item) {
        if (item == null) {
            return 0;
        }
        if (item instanceof String text) {
            if (looksLikeMediaPayload(text)) {
                return IMAGE_DEFAULT_TOKENS;
            }
            return estimateTokens(text);
        }
        if (!(item instanceof Map)) {
            return estimateTokens(String.valueOf(item));
        }
        Map<String, Object> map = (Map<String, Object>) item;
        if (isMediaPart(map)) {
            return IMAGE_DEFAULT_TOKENS;
        }
        if (map.containsKey("text")) {
            return estimateTokens(String.valueOf(map.get("text")));
        }
        return estimateTokens(stableJson(map));
    }

    private boolean isMediaPart(Map<String, Object> map) {
        Object type = map.get("type");
        String typeName = type == null ? "" : String.valueOf(type).toLowerCase();
        if (typeName.contains("image") || typeName.contains("file") || typeName.contains("document")
                || typeName.contains("audio") || typeName.contains("video")) {
            return true;
        }
        return map.containsKey("image_url")
                || map.containsKey("image")
                || map.containsKey("file")
                || map.containsKey("file_url")
                || map.containsKey("document")
                || map.containsKey("inline_data");
    }

    private boolean looksLikeMediaPayload(String text) {
        if (text == null) {
            return false;
        }
        String trimmed = text.trim();
        return trimmed.startsWith("data:image")
                || trimmed.startsWith("data:application")
                || trimmed.startsWith("data:audio")
                || trimmed.startsWith("data:video");
    }

    @SuppressWarnings("unchecked")
    public int countToolCalls(List<Map<String, Object>> toolCalls) {
        int tokenCount = 0;
        if (toolCalls == null) {
            return 0;
        }
        for (Map<String, Object> toolCall : toolCalls) {
            if (toolCall == null) {
                continue;
            }
            tokenCount += estimateTokens(String.valueOf(toolCall.getOrDefault("id", "")));
            tokenCount += estimateTokens(String.valueOf(toolCall.getOrDefault("type", "")));
            if (toolCall.containsKey("function")) {
                Map<String, Object> function = (Map<String, Object>) toolCall.get("function");
                tokenCount += estimateTokens(String.valueOf(function.getOrDefault("name", "")));
                tokenCount += estimateTokens(String.valueOf(function.getOrDefault("arguments", "")));
            }
        }
        return tokenCount;
    }

    public int countMessageTokens(Map<String, Object> message) {
        int tokens = BASE_MESSAGE_TOKENS;
        tokens += estimateTokens(message.getOrDefault("role", "").toString());
        if (message.containsKey("content")) {
            tokens += countContent(message.get("content"));
        }
        if (message.containsKey("reasoning_content") || message.containsKey("reasoningContent")) {
            Object reasoning = message.get("reasoning_content");
            if (reasoning == null) {
                reasoning = message.get("reasoningContent");
            }
            tokens += estimateTokens(reasoning == null ? null : String.valueOf(reasoning));
        }
        if (message.containsKey("tool_calls")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");
            tokens += countToolCalls(toolCalls);
        }
        tokens += estimateTokens(String.valueOf(message.getOrDefault("name", "")));
        tokens += estimateTokens(String.valueOf(message.getOrDefault("tool_call_id", "")));
        return tokens;
    }

    public int countListMessageTokens(List<Map<String, Object>> messages) {
        int totalTokens = FORMAT_TOKENS;
        if (messages == null) {
            return totalTokens;
        }
        for (Map<String, Object> message : messages) {
            totalTokens += countMessageTokens(message);
        }
        return totalTokens;
    }

    /**
     * 估算领域 Message 列表（不含 system）。含整包 format overhead。
     */
    public int estimateMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return FORMAT_TOKENS;
        }
        int total = FORMAT_TOKENS;
        for (Message message : messages) {
            total += estimateOneMessage(message);
        }
        return total;
    }

    /**
     * 只计算消息本身，不重复添加整包 format overhead。供 usage anchor 增量使用。
     */
    public int estimateMessageDelta(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (Message message : messages) {
            total += estimateOneMessage(message);
        }
        return total;
    }

    public int estimateOneMessage(Message message) {
        if (message == null) {
            return 0;
        }
        int tokens = BASE_MESSAGE_TOKENS;
        if (message.getRole() != null) {
            tokens += estimateTokens(message.getRole().name());
        }
        tokens += countContent(message.getContent());
        tokens += estimateTokens(message.getReasoningContent());
        tokens += estimateTokens(message.getToolCallId());
        if (StringUtils.isNotBlank(message.getBase64Image())) {
            tokens += IMAGE_DEFAULT_TOKENS;
        }
        if (message.getToolCalls() != null) {
            for (ToolCall toolCall : message.getToolCalls()) {
                if (toolCall == null) {
                    continue;
                }
                tokens += estimateTokens(toolCall.getId());
                tokens += estimateTokens(toolCall.getType());
                if (toolCall.getFunction() == null) {
                    continue;
                }
                tokens += estimateTokens(toolCall.getFunction().getName());
                tokens += estimateTokens(toolCall.getFunction().getArguments());
            }
        }
        return tokens;
    }

    public int estimateTools(ToolCollection tools) {
        if (tools == null) {
            return 0;
        }
        int tokens = 0;
        if (tools.getToolMap() != null) {
            for (BaseTool tool : tools.getToolMap().values()) {
                tokens += estimateOneToolSchema(tool);
            }
        }
        if (tools.getMcpToolMap() != null) {
            for (McpToolInfo mcp : tools.getMcpToolMap().values()) {
                tokens += estimateOneMcpSchema(mcp);
            }
        }
        return tokens;
    }

    private int estimateOneToolSchema(BaseTool tool) {
        if (tool == null) {
            return 0;
        }
        int tokens = estimateTokens(tool.getName());
        tokens += estimateTokens(tool.getDescription());
        try {
            Object params = tool.toParams();
            if (params != null) {
                tokens += estimateTokens(stableJson(params));
            }
        } catch (Exception ignored) {
            // ignore schema reflection failures
        }
        return tokens;
    }

    private int estimateOneMcpSchema(McpToolInfo mcp) {
        if (mcp == null) {
            return 0;
        }
        int tokens = estimateTokens(mcp.getName());
        tokens += estimateTokens(mcp.getDesc());
        if (StringUtils.isNotBlank(mcp.getParameters())) {
            tokens += estimateTokens(stableJsonFromRaw(mcp.getParameters()));
        }
        return tokens;
    }

    public List<String> listToolNames(ToolCollection tools) {
        List<String> names = new ArrayList<>();
        if (tools == null) {
            return names;
        }
        if (tools.getToolMap() != null) {
            names.addAll(tools.getToolMap().keySet());
        }
        if (tools.getMcpToolMap() != null) {
            names.addAll(tools.getMcpToolMap().keySet());
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public String toolSchemaFingerprint(ToolCollection tools) {
        return shortHash(stableToolSchema(tools));
    }

    public String fingerprint(PromptShape shape) {
        if (shape == null) {
            return shortHash("null");
        }
        String systemContent = shape.getSystemMessage() == null
                ? ""
                : StringUtils.defaultString(shape.getSystemMessage().getContent());
        String toolFp = toolSchemaFingerprint(shape.getTools());
        return shortHash(shape.protocolName() + "|" + systemContent + "|" + toolFp);
    }

    /**
     * 请求前完整粗估 + 指纹（用于 prompt cache debug）。默认按 function_call 口径。
     */
    public PromptEstimate estimatePrompt(Message systemMessage, List<Message> messages, ToolCollection tools) {
        return estimatePrompt(PromptShape.functionCall(systemMessage, messages, tools));
    }

    public PromptEstimate estimatePrompt(PromptShape shape) {
        PromptShape effective = shape == null ? PromptShape.text(null, List.of()) : shape;
        String systemContent = effective.getSystemMessage() == null
                ? ""
                : StringUtils.defaultString(effective.getSystemMessage().getContent());
        int systemTokens = estimateTokens(systemContent);
        int messageTokens = estimateMessages(effective.getMessages());
        int toolTokens = effective.isIncludeToolTokens() ? estimateTools(effective.getTools()) : 0;
        List<String> toolNames = listToolNames(effective.getTools());
        Map<String, Integer> roleCounts = new LinkedHashMap<>();
        if (effective.getMessages() != null) {
            for (Message message : effective.getMessages()) {
                String role = message == null || message.getRole() == null ? "null" : message.getRole().name();
                roleCounts.merge(role, 1, Integer::sum);
            }
        }
        String toolSchemaFingerprint = toolSchemaFingerprint(effective.getTools());
        String promptShapeFingerprint = fingerprint(effective);
        return PromptEstimate.builder()
                .systemChars(systemContent.length())
                .systemTokens(systemTokens)
                .systemFingerprint(shortHash(systemContent))
                .messageCount(effective.getMessages() == null ? 0 : effective.getMessages().size())
                .messageTokens(messageTokens)
                .roleCounts(roleCounts)
                .toolCount(toolNames.size())
                .toolNames(toolNames)
                .toolTokens(toolTokens)
                .toolSchemaFingerprint(toolSchemaFingerprint)
                .promptShapeFingerprint(promptShapeFingerprint)
                .estimateSource(SOURCE_LOCAL_ESTIMATE)
                .estimatedTotalTokens(systemTokens + messageTokens + toolTokens)
                .build();
    }

    public String stableToolSchema(ToolCollection tools) {
        if (tools == null) {
            return "";
        }
        Map<String, Object> root = new TreeMap<>();
        if (tools.getToolMap() != null) {
            for (BaseTool tool : tools.getToolMap().values()) {
                if (tool == null || StringUtils.isBlank(tool.getName())) {
                    continue;
                }
                Map<String, Object> one = new TreeMap<>();
                one.put("name", tool.getName());
                one.put("description", StringUtils.defaultString(tool.getDescription()));
                try {
                    one.put("parameters", tool.toParams());
                } catch (Exception ignored) {
                    one.put("parameters", Map.of());
                }
                root.put("local:" + tool.getName(), one);
            }
        }
        if (tools.getMcpToolMap() != null) {
            for (McpToolInfo mcp : tools.getMcpToolMap().values()) {
                if (mcp == null || StringUtils.isBlank(mcp.getName())) {
                    continue;
                }
                Map<String, Object> one = new TreeMap<>();
                one.put("name", mcp.getName());
                one.put("description", StringUtils.defaultString(mcp.getDesc()));
                one.put("parameters", parseJsonOrRaw(mcp.getParameters()));
                root.put("mcp:" + mcp.getName(), one);
            }
        }
        return stableJson(root);
    }

    public String stableJson(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String text) {
            return stableJsonFromRaw(text);
        }
        try {
            return ToolSchemaNormalizer.toStableJson(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private String stableJsonFromRaw(String raw) {
        if (StringUtils.isBlank(raw)) {
            return "";
        }
        try {
            Object parsed = JSON.parse(raw);
            return ToolSchemaNormalizer.toStableJson(parsed);
        } catch (Exception e) {
            return raw;
        }
    }

    private Object parseJsonOrRaw(String raw) {
        if (StringUtils.isBlank(raw)) {
            return "";
        }
        try {
            return JSON.parse(raw);
        } catch (Exception e) {
            return raw;
        }
    }

    public String shortHash(String text) {
        if (text == null) {
            return "null";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 12);
        } catch (Exception e) {
            return Integer.toHexString(text.hashCode());
        }
    }

    public String summarizeRoles(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return "[]";
        }
        return messages.stream()
                .map(m -> m == null || m.getRole() == null ? "?" : shortRole(m.getRole()))
                .collect(Collectors.joining(","));
    }

    private String shortRole(RoleType role) {
        return switch (role) {
            case USER -> "U";
            case ASSISTANT -> "A";
            case TOOL -> "T";
            case SYSTEM -> "S";
        };
    }

    @Data
    @Builder
    public static class PromptEstimate {
        private int systemChars;
        private int systemTokens;
        private String systemFingerprint;
        private int messageCount;
        private int messageTokens;
        private Map<String, Integer> roleCounts;
        private int toolCount;
        private List<String> toolNames;
        private int toolTokens;
        private int estimatedTotalTokens;
        private String toolSchemaFingerprint;
        private String promptShapeFingerprint;
        private String estimateSource;

        public String toLogLine() {
            return "estTotal=" + estimatedTotalTokens
                    + " source=" + estimateSource
                    + " system(chars=" + systemChars + ",tok~" + systemTokens + ",fp=" + systemFingerprint + ")"
                    + " messages(n=" + messageCount + ",tok~" + messageTokens + ",roles=" + roleCounts + ")"
                    + " tools(n=" + toolCount + ",tok~" + toolTokens + ",schemaFp=" + toolSchemaFingerprint + ",names=" + toolNames + ")"
                    + " shapeFp=" + promptShapeFingerprint;
        }
    }
}
