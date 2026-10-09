package org.wwz.ai.domain.agent.runtime.llm;

import lombok.Builder;
import lombok.Getter;

/**
 * 供应商无关的工具定义。
 * <p>
 * invoker 仅用于保留工具回调的执行语义；如何包装成供应商 callback 属于 Infrastructure。
 * 不可变契约：字段 final，需要替换 invoker 时使用 {@link #withInvoker(LlmToolInvoker)}。
 */
@Getter
@Builder(toBuilder = true)
public class LlmToolDefinition {

    private final String name;
    private final String description;
    private final String inputSchema;
    private final LlmToolInvoker invoker;

    private LlmToolDefinition(String name, String description, String inputSchema, LlmToolInvoker invoker) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
        this.invoker = invoker;
    }

    public LlmToolDefinition withInvoker(LlmToolInvoker toolInvoker) {
        return LlmToolDefinition.builder()
                .name(name)
                .description(description)
                .inputSchema(inputSchema)
                .invoker(toolInvoker)
                .build();
    }

    @FunctionalInterface
    public interface LlmToolInvoker {
        Object invoke(String input) throws Exception;
    }
}
