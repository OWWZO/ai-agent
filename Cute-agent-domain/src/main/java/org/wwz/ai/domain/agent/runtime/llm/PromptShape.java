package org.wwz.ai.domain.agent.runtime.llm;

import lombok.Builder;
import lombok.Value;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;

import java.util.List;

/**
 * 当前真实请求形状。估算、观测和压缩必须共用这一口径。
 */
@Value
@Builder(toBuilder = true)
public class PromptShape {

    LlmAskToolProtocol protocol;
    Message systemMessage;
    List<Message> messages;
    ToolCollection tools;
    /** function_call 才把 tools schema 单独计入；struct_parse 已拼进 system。 */
    boolean includeToolTokens;

    public PromptShape withMessages(List<Message> newMessages) {
        return toBuilder().messages(newMessages).build();
    }

    public static PromptShape functionCall(Message systemMessage, List<Message> messages, ToolCollection tools) {
        return PromptShape.builder()
                .protocol(LlmAskToolProtocol.FUNCTION_CALL)
                .systemMessage(systemMessage)
                .messages(messages)
                .tools(tools)
                .includeToolTokens(true)
                .build();
    }

    public static PromptShape structParse(Message systemMessage, List<Message> messages, ToolCollection tools) {
        return PromptShape.builder()
                .protocol(LlmAskToolProtocol.STRUCT_PARSE)
                .systemMessage(systemMessage)
                .messages(messages)
                .tools(tools)
                .includeToolTokens(false)
                .build();
    }

    public static PromptShape text(Message systemMessage, List<Message> messages) {
        return PromptShape.builder()
                .protocol(LlmAskToolProtocol.TEXT)
                .systemMessage(systemMessage)
                .messages(messages)
                .tools(null)
                .includeToolTokens(false)
                .build();
    }

    public String protocolName() {
        return protocol == null ? LlmAskToolProtocol.FUNCTION_CALL.name() : protocol.name();
    }
}
