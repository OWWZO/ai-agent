package org.wwz.ai.domain.agent.runtime.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.util.ToolSchemaNormalizer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 本地 BaseTool 的 Spring AI ToolCallback 适配器。
 * 默认仍由 Agent 手动执行工具；这里主要用于向模型声明工具协议，
 * 同时保留误开启内部执行时的兼容能力。
 */
@Slf4j
@RequiredArgsConstructor
public class BaseToolCallbackAdapter implements ToolCallback {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final BaseTool tool;

    @Override
    public ToolDefinition getToolDefinition() {
        return DefaultToolDefinition.builder()
                .name(tool.getName())
                .description(StringUtils.defaultString(tool.getDescription()))
                .inputSchema(writeSchema(tool))
                .build();
    }

    @Override
    public String call(String toolInput) {
        return execute(toolInput);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return execute(toolInput);
    }

    /**
     * 将 Spring AI 内部工具调用兼容到现有 BaseTool.execute 语义。
     */
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

    /**
     * 兼容对象参数与原始字符串参数两种输入形式。
     */
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

    /**
     * Spring AI 的 ToolDefinition.inputSchema 要求字符串，这里统一序列化为 JSON Schema 文本。
     */
    private String writeSchema(BaseTool tool) {
        try {
            return OBJECT_MAPPER.writeValueAsString(
                    ToolSchemaNormalizer.normalizeSchema(tool.toParams(), tool.getName())
            );
        } catch (Exception e) {
            throw new IllegalStateException("序列化工具 Schema 失败: " + tool.getName(), e);
        }
    }
}
