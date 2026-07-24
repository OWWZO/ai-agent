package org.wwz.ai.domain.agent.runtime.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.util.ToolSchemaNormalizer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 本地 BaseTool 的 Spring AI ToolCallback 适配器。
 * ToolDefinition 经 ToolDefinitionCache 复用，inputSchema 固定排序序列化，服务 prompt cache。
 */
@Slf4j
@RequiredArgsConstructor
public class BaseToolCallbackAdapter implements ToolCallback {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final BaseTool tool;

    /** 首次解析后缓存，避免同实例重复 normalize */
    private volatile ToolDefinition cachedDefinition;

    @Override
    public ToolDefinition getToolDefinition() {
        ToolDefinition local = cachedDefinition;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (cachedDefinition == null) {
                cachedDefinition = ToolDefinitionCache.getOrCreateFromMap(
                        tool.getName(),
                        StringUtils.defaultString(tool.getDescription()),
                        tool.toParams()
                );
            }
            return cachedDefinition;
        }
    }

    @Override
    public String call(String toolInput) {
        return execute(toolInput);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return execute(toolInput);
    }

    private String execute(String toolInput) {
        try {
            Object parsedInput = parseToolInput(toolInput);
            Object result = tool.execute(parsedInput);
            if (result == null) {
                return "";
            }
            if (result instanceof String stringResult) {
                return stringResult;
            }
            return OBJECT_MAPPER.writeValueAsString(result);
        } catch (Exception e) {
            log.error("BaseTool ToolCallback 调用失败: tool={}, input={}", tool.getName(), toolInput, e);
            throw new RuntimeException("BaseTool callback execute failed: " + tool.getName(), e);
        }
    }

    private Object parseToolInput(String toolInput) {
        if (StringUtils.isBlank(toolInput)) {
            return new LinkedHashMap<String, Object>();
        }
        try {
            return OBJECT_MAPPER.readValue(toolInput, Object.class);
        } catch (Exception ignore) {
            return toolInput;
        }
    }
}
