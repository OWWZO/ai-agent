package org.wwz.ai.domain.agent.runtime.llm;

import lombok.Builder;
import lombok.Getter;
import org.wwz.ai.domain.agent.runtime.enums.RoleType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 供应商无关的 LLM 消息。
 * <p>
 * 不可变契约：所有字段 final，工具调用列表做防御性复制；需要变体时用 {@code toBuilder()}。
 */
@Getter
@Builder(toBuilder = true)
public class LlmMessage {

    private final RoleType role;
    private final String content;
    private final String reasoningContent;
    private final String base64Image;
    private final String toolCallId;
    private final List<LlmToolCall> toolCalls;

    private LlmMessage(RoleType role,
                       String content,
                       String reasoningContent,
                       String base64Image,
                       String toolCallId,
                       List<LlmToolCall> toolCalls) {
        this.role = role;
        this.content = content;
        this.reasoningContent = reasoningContent;
        this.base64Image = base64Image;
        this.toolCallId = toolCallId;
        this.toolCalls = toolCalls == null ? null : Collections.unmodifiableList(new ArrayList<>(toolCalls));
    }
}
