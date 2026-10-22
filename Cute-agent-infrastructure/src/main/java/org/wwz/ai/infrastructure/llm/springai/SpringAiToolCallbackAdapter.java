package org.wwz.ai.infrastructure.llm.springai;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.wwz.ai.domain.agent.runtime.llm.LlmToolDefinition;

/**
 * Domain tool definition 到 Spring AI ToolCallback 的适配器。
 */
@Slf4j
@RequiredArgsConstructor
public class SpringAiToolCallbackAdapter implements ToolCallback {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final LlmToolDefinition definition;
    private final ToolDefinition springDefinition;

    public SpringAiToolCallbackAdapter(LlmToolDefinition definition) {
        this.definition = definition;
        this.springDefinition = DefaultToolDefinition.builder()
                .name(StringUtils.defaultString(definition.getName()))
                .description(StringUtils.defaultString(definition.getDescription()))
                .inputSchema(StringUtils.defaultIfBlank(definition.getInputSchema(), "{}"))
                .build();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return springDefinition;
    }

    @Override
    public String call(String toolInput) {
        return invoke(toolInput);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return invoke(toolInput);
    }

    private String invoke(String toolInput) {
        try {
            if (definition.getInvoker() == null) {
                return "";
            }
            Object result = definition.getInvoker().invoke(toolInput);
            if (result == null) {
                return "";
            }
            return result instanceof String stringResult
                    ? stringResult
                    : OBJECT_MAPPER.writeValueAsString(result);
        } catch (Exception e) {
            log.error("Spring AI ToolCallback 调用失败: tool={}, input={}", definition.getName(), toolInput, e);
            throw new RuntimeException("Tool callback execute failed: " + definition.getName(), e);
        }
    }
}
