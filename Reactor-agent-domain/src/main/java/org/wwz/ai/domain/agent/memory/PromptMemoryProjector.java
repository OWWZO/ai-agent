package org.wwz.ai.domain.agent.memory;

import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.runtime.enums.RoleType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 在运行时 Memory 与可持久化提示词记忆消息之间进行无损转换。
 */
public class PromptMemoryProjector {

    /**
     * 投影基线后的完整消息，未完成工具调用尾缀不会进入持久化流。
     */
    public List<PromptMemoryMessage> project(List<Message> memory, int baseline) {
        List<Message> validMessages = validPrefix(memory);
        int startIndex = Math.min(Math.max(baseline, 0), validMessages.size());
        List<PromptMemoryMessage> rows = new ArrayList<>();
        for (int i = startIndex; i < validMessages.size(); i++) {
            rows.add(toPromptMemoryMessage(validMessages.get(i)));
        }
        return rows;
    }

    /**
     * 从持久化投影重建独立的运行时消息，避免共享可变工具调用对象。
     */
    public List<Message> hydrate(List<PromptMemoryMessage> rows) {
        List<Message> messages = new ArrayList<>();
        if (rows == null) {
            return messages;
        }
        for (PromptMemoryMessage row : rows) {
            if (row == null) {
                continue;
            }
            messages.add(Message.builder()
                    .role(row.getRole())
                    .content(row.getContent())
                    .base64Image(row.getBase64Image())
                    .toolCallId(row.getToolCallId())
                    .toolCalls(copyToolCalls(row.getToolCalls()))
                    .build());
        }
        return messages;
    }

    /**
     * 仅剔除尚未收到全部工具响应的 assistant 工具调用尾缀。
     */
    public List<Message> validPrefix(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return new ArrayList<>();
        }
        int incompleteSuffixStart = findIncompleteSuffixStart(messages);
        int endIndex = incompleteSuffixStart < 0 ? messages.size() : incompleteSuffixStart;
        return new ArrayList<>(messages.subList(0, endIndex));
    }

    private int findIncompleteSuffixStart(List<Message> messages) {
        for (int messageIndex = 0; messageIndex < messages.size(); messageIndex++) {
            Message message = messages.get(messageIndex);
            if (!hasToolCalls(message) || allToolCallsCompleted(messages, messageIndex, message.getToolCalls())) {
                continue;
            }
            return messageIndex;
        }
        return -1;
    }

    private boolean hasToolCalls(Message message) {
        return message != null
                && message.getRole() == RoleType.ASSISTANT
                && message.getToolCalls() != null
                && !message.getToolCalls().isEmpty();
    }

    private boolean allToolCallsCompleted(List<Message> messages, int assistantIndex, List<ToolCall> toolCalls) {
        Set<String> completedToolCallIds = new HashSet<>();
        for (int messageIndex = assistantIndex + 1; messageIndex < messages.size(); messageIndex++) {
            Message message = messages.get(messageIndex);
            if (message != null && message.getRole() == RoleType.TOOL && message.getToolCallId() != null) {
                completedToolCallIds.add(message.getToolCallId());
            }
        }
        for (ToolCall toolCall : toolCalls) {
            if (toolCall == null || toolCall.getId() == null || !completedToolCallIds.contains(toolCall.getId())) {
                return false;
            }
        }
        return true;
    }

    private PromptMemoryMessage toPromptMemoryMessage(Message message) {
        return PromptMemoryMessage.builder()
                .role(message.getRole())
                .content(message.getContent())
                .base64Image(message.getBase64Image())
                .toolCallId(message.getToolCallId())
                .toolCalls(copyToolCalls(message.getToolCalls()))
                .build();
    }

    private List<ToolCall> copyToolCalls(List<ToolCall> toolCalls) {
        if (toolCalls == null) {
            return null;
        }
        List<ToolCall> copies = new ArrayList<>(toolCalls.size());
        for (ToolCall toolCall : toolCalls) {
            copies.add(copyToolCall(toolCall));
        }
        return copies;
    }

    private ToolCall copyToolCall(ToolCall toolCall) {
        if (toolCall == null) {
            return null;
        }
        ToolCall.Function function = toolCall.getFunction();
        return ToolCall.builder()
                .id(toolCall.getId())
                .type(toolCall.getType())
                .function(function == null ? null : ToolCall.Function.builder()
                        .name(function.getName())
                        .arguments(function.getArguments())
                        .build())
                .build();
    }
}
