package org.wwz.ai.domain.agent.runtime.llm;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * Token 计数器类
 */
@Slf4j
public class TokenCounter {
    // Token 常量
    private static final int BASE_MESSAGE_TOKENS = 4;
    private static final int FORMAT_TOKENS = 2;
    private static final int LOW_DETAIL_IMAGE_TOKENS = 85;
    private static final int HIGH_DETAIL_TILE_TOKENS = 170;

    // 图像处理常量
    private static final int MAX_SIZE = 2048;
    private static final int HIGH_DETAIL_TARGET_SHORT_SIDE = 768;
    private static final int TILE_SIZE = 512;

    public TokenCounter() {
    }

    /**
     * 计算文本的 token 数量
     */
    public int countText(String text) {
        return text == null ? 0 : text.length();
    }

    /**
     * 计算图像的 token 数量
     */
    public int countImage(Map<String, Object> imageItem) {
        String detail = (String) imageItem.getOrDefault("detail", "medium");

        // 低细节级别固定返回 85 个 token
        if ("low".equals(detail)) {
            return LOW_DETAIL_IMAGE_TOKENS;
        }

        // 高细节级别根据尺寸计算
        if ("high".equals(detail) || "medium".equals(detail)) {
            if (imageItem.containsKey("dimensions")) {
                List<Integer> dimensions = (List<Integer>) imageItem.get("dimensions");
                return calculateHighDetailTokens(dimensions.get(0), dimensions.get(1));
            }
        }

        // 默认值
        if ("high".equals(detail)) {
            return calculateHighDetailTokens(1024, 1024); // 765 tokens
        } else if ("medium".equals(detail)) {
            return 1024;
        } else {
            return 1024; // 默认使用中等大小
        }
    }

    /**
     * 计算高细节图像的 token 数量
     */
    private int calculateHighDetailTokens(int width, int height) {
        // 步骤1：缩放到 MAX_SIZE x MAX_SIZE 正方形内
        if (width > MAX_SIZE || height > MAX_SIZE) {
            double scale = MAX_SIZE / (double) Math.max(width, height);
            width = (int) (width * scale);
            height = (int) (height * scale);
        }

        // 步骤2：缩放最短边到 HIGH_DETAIL_TARGET_SHORT_SIDE
        double scale = HIGH_DETAIL_TARGET_SHORT_SIDE / (double) Math.min(width, height);
        int scaledWidth = (int) (width * scale);
        int scaledHeight = (int) (height * scale);

        // 步骤3：计算 512px 瓦片数量
        int tilesX = (int) Math.ceil(scaledWidth / (double) TILE_SIZE);
        int tilesY = (int) Math.ceil(scaledHeight / (double) TILE_SIZE);
        int totalTiles = tilesX * tilesY;

        // 步骤4：计算最终 token 数量
        return (totalTiles * HIGH_DETAIL_TILE_TOKENS) + LOW_DETAIL_IMAGE_TOKENS;
    }

    /**
     * 计算消息内容的 token 数量
     */
    public int countContent(Object content) {
        if (content == null) {
            return 0;
        }

        if (content instanceof String) {
            return countText((String) content);
        }

        if (content instanceof List) {
            int tokenCount = 0;
            for (Object item : (List<?>) content) {
                if (item instanceof String) {
                    tokenCount += countText((String) item);
                } else if (item instanceof Map) {
                    Map<String, Object> map = (Map<String, Object>) item;
                    if (map.containsKey("text")) {
                        tokenCount += countText((String) map.get("text"));
                    } else if (map.containsKey("image_url")) {
                        tokenCount += countImage((Map<String, Object>) map.get("image_url"));
                    }
                }
            }
            return tokenCount;
        }

        return 0;
    }

    /**
     * 计算工具调用的 token 数量
     */
    public int countToolCalls(List<Map<String, Object>> toolCalls) {
        int tokenCount = 0;
        for (Map<String, Object> toolCall : toolCalls) {
            if (toolCall.containsKey("function")) {
                Map<String, Object> function = (Map<String, Object>) toolCall.get("function");
                tokenCount += countText((String) function.getOrDefault("name", ""));
                tokenCount += countText((String) function.getOrDefault("arguments", ""));
            }
        }
        return tokenCount;
    }


    public int countMessageTokens(Map<String, Object> message) {
        int tokens = BASE_MESSAGE_TOKENS; // 每条消息的基础 token

        // 添加角色 token
        tokens += countText(message.getOrDefault("role", "").toString());

        // 添加内容 token
        if (message.containsKey("content")) {
            tokens += countContent(message.get("content"));
        }

        // 添加工具调用 token
        if (message.containsKey("tool_calls")) {
            tokens += countToolCalls((List<Map<String, Object>>) message.get("tool_calls"));
        }

        // 添加名称和工具调用 ID token
        tokens += countText((String) message.getOrDefault("name", ""));
        tokens += countText((String) message.getOrDefault("tool_call_id", ""));

        return tokens;
    }

    /**
     * 计算消息列表的总 token 数量
     */
    public int countListMessageTokens(List<Map<String, Object>> messages) {
        int totalTokens = FORMAT_TOKENS; // 基础格式 token
        for (Map<String, Object> message : messages) {
            totalTokens += countMessageTokens(message);
        }
        return totalTokens;
    }
}