package org.wwz.ai.domain.agent.runtime.llm;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.runtime.enums.RoleType;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.dto.tool.McpToolInfo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Token 粗估器（默认按约 4 chars/token 估算）。
 * <p>
 * 说明：{@link #countText(String)} 仍返回<strong>字符数</strong>（兼容 history 预算等旧调用）。
 * 真正 token 粗估请用 {@link #estimateTokens(String)} / {@link #estimatePrompt}。
 */
@Slf4j
public class TokenCounter {

    /** 每 token 默认按 4 字节估算。 */
    public static final int CHARS_PER_TOKEN = 4;
    private static final int BASE_MESSAGE_TOKENS = 4;
    private static final int FORMAT_TOKENS = 2;
    private static final int LOW_DETAIL_IMAGE_TOKENS = 85;
    private static final int HIGH_DETAIL_TILE_TOKENS = 170;
    private static final int MAX_SIZE = 2048;
    private static final int HIGH_DETAIL_TARGET_SHORT_SIDE = 768;
    private static final int TILE_SIZE = 512;
    private static final int IMAGE_DEFAULT_TOKENS = 2000;

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
        String detail = (String) imageItem.getOrDefault("detail", "medium");
        if ("low".equals(detail)) {
            return LOW_DETAIL_IMAGE_TOKENS;
        }
        if ("high".equals(detail) || "medium".equals(detail)) {
            if (imageItem.containsKey("dimensions")) {
                @SuppressWarnings("unchecked")
                List<Integer> dimensions = (List<Integer>) imageItem.get("dimensions");
                return calculateHighDetailTokens(dimensions.get(0), dimensions.get(1));
            }
        }
        if ("high".equals(detail)) {
            return calculateHighDetailTokens(1024, 1024);
        }
        return 1024;
    }

    private int calculateHighDetailTokens(int width, int height) {
        if (width > MAX_SIZE || height > MAX_SIZE) {
            double scale = MAX_SIZE / (double) Math.max(width, height);
            width = (int) (width * scale);
            height = (int) (height * scale);
        }
        double scale = HIGH_DETAIL_TARGET_SHORT_SIDE / (double) Math.min(width, height);
        int scaledWidth = (int) (width * scale);
        int scaledHeight = (int) (height * scale);
        int tilesX = (int) Math.ceil(scaledWidth / (double) TILE_SIZE);
        int tilesY = (int) Math.ceil(scaledHeight / (double) TILE_SIZE);
        return (tilesX * tilesY * HIGH_DETAIL_TILE_TOKENS) + LOW_DETAIL_IMAGE_TOKENS;
    }

    public int countContent(Object content) {
        if (content == null) {
            return 0;
        }
        if (content instanceof String) {
            return estimateTokens((String) content);
        }
        if (content instanceof List) {
            int tokenCount = 0;
            for (Object item : (List<?>) content) {
                if (item instanceof String) {
                    tokenCount += estimateTokens((String) item);
                } else if (item instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) item;
                    if (map.containsKey("text")) {
                        tokenCount += estimateTokens(String.valueOf(map.get("text")));
                    } else if (map.containsKey("image_url")) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> image = (Map<String, Object>) map.get("image_url");
                        tokenCount += countImage(image);
                    }
                }
            }
            return tokenCount;
        }
        return estimateTokens(String.valueOf(content));
    }

    @SuppressWarnings("unchecked")
    public int countToolCalls(List<Map<String, Object>> toolCalls) {
        int tokenCount = 0;
        if (toolCalls == null) {
            return 0;
        }
        for (Map<String, Object> toolCall : toolCalls) {
            if (toolCall != null && toolCall.containsKey("function")) {
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
     * 估算领域 Message 列表（不含 system）。
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

    public int estimateOneMessage(Message message) {
        if (message == null) {
            return 0;
        }
        int tokens = BASE_MESSAGE_TOKENS;
        if (message.getRole() != null) {
            tokens += estimateTokens(message.getRole().name());
        }
        tokens += estimateTokens(message.getContent());
        tokens += estimateTokens(message.getToolCallId());
        if (StringUtils.isNotBlank(message.getBase64Image())) {
            tokens += IMAGE_DEFAULT_TOKENS;
        }
        if (message.getToolCalls() != null) {
            for (ToolCall toolCall : message.getToolCalls()) {
                if (toolCall == null || toolCall.getFunction() == null) {
                    continue;
                }
                tokens += estimateTokens(toolCall.getId());
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
                if (tool == null) {
                    continue;
                }
                tokens += estimateTokens(tool.getName());
                tokens += estimateTokens(tool.getDescription());
                try {
                    Object params = tool.toParams();
                    if (params != null) {
                        tokens += estimateTokens(String.valueOf(params));
                    }
                } catch (Exception ignored) {
                    // ignore schema reflection failures
                }
            }
        }
        if (tools.getMcpToolMap() != null) {
            for (McpToolInfo mcp : tools.getMcpToolMap().values()) {
                if (mcp == null) {
                    continue;
                }
                tokens += estimateTokens(mcp.getName());
                tokens += estimateTokens(mcp.getDesc());
            }
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

    /**
     * 请求前完整粗估 + 指纹（用于 prompt cache debug）。
     */
    public PromptEstimate estimatePrompt(Message systemMessage, List<Message> messages, ToolCollection tools) {
        String systemContent = systemMessage == null ? "" : StringUtils.defaultString(systemMessage.getContent());
        int systemTokens = estimateTokens(systemContent);
        int messageTokens = estimateMessages(messages);
        int toolTokens = estimateTools(tools);
        List<String> toolNames = listToolNames(tools);
        Map<String, Integer> roleCounts = new LinkedHashMap<>();
        if (messages != null) {
            for (Message message : messages) {
                String role = message == null || message.getRole() == null ? "null" : message.getRole().name();
                roleCounts.merge(role, 1, Integer::sum);
            }
        }
        return PromptEstimate.builder()
                .systemChars(systemContent.length())
                .systemTokens(systemTokens)
                .systemFingerprint(shortHash(systemContent))
                .messageCount(messages == null ? 0 : messages.size())
                .messageTokens(messageTokens)
                .roleCounts(roleCounts)
                .toolCount(toolNames.size())
                .toolNames(toolNames)
                .toolTokens(toolTokens)
                .estimatedTotalTokens(systemTokens + messageTokens + toolTokens)
                .build();
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

        public String toLogLine() {
            return "estTotal=" + estimatedTotalTokens
                    + " system(chars=" + systemChars + ",tok~" + systemTokens + ",fp=" + systemFingerprint + ")"
                    + " messages(n=" + messageCount + ",tok~" + messageTokens + ",roles=" + roleCounts + ")"
                    + " tools(n=" + toolCount + ",tok~" + toolTokens + ",names=" + toolNames + ")";
        }
    }
}
