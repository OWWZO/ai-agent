package org.wwz.ai.domain.agent.runtime.llm;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.util.ToolSchemaNormalizer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

/**
 * 工具定义（name/description/inputSchema）进程级缓存。
 * 同一工具协议内容复用同一领域定义，降低每轮重建导致的 tools 前缀漂移。
 */
public final class ToolDefinitionCache {

    static final long MAXIMUM_SIZE = 4096;
    static final long EXPIRE_AFTER_ACCESS_MS = TimeUnit.MINUTES.toMillis(30);

    private static final Cache<String, LlmToolDefinition> CACHE = CacheBuilder.newBuilder()
            .maximumSize(MAXIMUM_SIZE)
            .expireAfterAccess(EXPIRE_AFTER_ACCESS_MS, TimeUnit.MILLISECONDS)
            .build();

    private ToolDefinitionCache() {
    }

    public static LlmToolDefinition getOrCreate(String name, String description, String inputSchemaJson) {
        String toolName = StringUtils.defaultString(name);
        String desc = StringUtils.defaultString(description);
        String schema = StringUtils.defaultString(inputSchemaJson);
        String key = toolName + "\0" + sha12(desc + "\0" + schema);
        return CACHE.asMap().computeIfAbsent(key, k -> LlmToolDefinition.builder()
                .name(toolName)
                .description(desc)
                .inputSchema(schema)
                .build());
    }

    public static LlmToolDefinition getOrCreateFromMap(String name, String description, Map<String, Object> rawSchema) {
        String schema = ToolSchemaNormalizer.normalizeSchemaStable(rawSchema, name);
        return getOrCreate(name, description, schema);
    }

    public static LlmToolDefinition getOrCreateFromRawSchemaString(String name, String description, String rawSchema) {
        String schema = ToolSchemaNormalizer.normalizeSchema(rawSchema, name);
        return getOrCreate(name, description, schema);
    }

    public static int size() {
        CACHE.cleanUp();
        return (int) CACHE.size();
    }

    private static String sha12(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 12);
        } catch (Exception e) {
            return Integer.toHexString(text.hashCode());
        }
    }
}
