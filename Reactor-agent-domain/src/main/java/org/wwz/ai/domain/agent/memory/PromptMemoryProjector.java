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
            validatePersistedRow(row);
            messages.add(Message.builder()
                    .role(row.getRole())
                    .content(row.getContent())
                    .base64Image(row.getBase64Image())
                    .toolCallId(row.getToolCallId())
                    .toolCalls(copyToolCalls(row.getToolCalls()))
                    .build());
        }
        validateCompleteToolBlocks(messages);
        return messages;
    }

    /**
     * 仅剔除尚未收到全部工具响应的 assistant 工具调用尾缀。
     */
    public List<Message> validPrefix(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return new ArrayList<>();
        }
        for (int messageIndex = 0; messageIndex < messages.size(); messageIndex++) {
            Message message = messages.get(messageIndex);
            validateRuntimeMessage(message);
            if (message.getRole() == RoleType.TOOL) {
                throw new IllegalArgumentException("TOOL 消息必须紧随关联的 assistant 工具调用");
            }
            if (!hasToolCalls(message)) {
                continue;
            }
            int responseBlockEnd = consumeToolResponseBlock(messages, messageIndex, message.getToolCalls());
            if (responseBlockEnd < 0) {
                return new ArrayList<>(messages.subList(0, messageIndex));
            }
            messageIndex = responseBlockEnd;
        }
        return new ArrayList<>(messages);
    }

    private boolean hasToolCalls(Message message) {
        return message != null
                && message.getRole() == RoleType.ASSISTANT
                && message.getToolCalls() != null
                && !message.getToolCalls().isEmpty();
    }

    private int consumeToolResponseBlock(List<Message> messages, int assistantIndex, List<ToolCall> toolCalls) {
        Set<String> pendingToolCallIds = new HashSet<>();
        for (ToolCall toolCall : toolCalls) {
            if (toolCall == null || toolCall.getId() == null || toolCall.getId().isBlank()) {
                return -1;
            }
            if (!pendingToolCallIds.add(toolCall.getId())) {
                throw new IllegalArgumentException("assistant 消息包含重复 toolCallId: " + toolCall.getId());
            }
        }
        for (int messageIndex = assistantIndex + 1; messageIndex < messages.size(); messageIndex++) {
            Message message = messages.get(messageIndex);
            validateRuntimeMessage(message);
            if (message.getRole() != RoleType.TOOL) {
                return pendingToolCallIds.isEmpty() ? messageIndex - 1 : -1;
            }
            if (!pendingToolCallIds.remove(message.getToolCallId())) {
                throw new IllegalArgumentException("TOOL 响应未匹配当前 assistant 的待执行工具调用: " + message.getToolCallId());
            }
        }
        return pendingToolCallIds.isEmpty() ? messages.size() - 1 : -1;
    }

    /**
     * 持久化记录必须是完整的工具调用块，不能包含未关联的 TOOL 消息。
     */
    private void validateCompleteToolBlocks(List<Message> messages) {
        for (int messageIndex = 0; messageIndex < messages.size(); messageIndex++) {
            Message message = messages.get(messageIndex);
            validateRuntimeMessage(message);
            if (message.getRole() == RoleType.TOOL) {
                throw new IllegalArgumentException("持久化 TOOL 消息缺少关联的 assistant 工具调用");
            }
            if (!hasToolCalls(message)) {
                continue;
            }
            int responseBlockEnd = consumeToolResponseBlock(messages, messageIndex, message.getToolCalls());
            if (responseBlockEnd < 0) {
                throw new IllegalArgumentException("持久化 assistant 工具调用缺少完整 TOOL 响应块");
            }
            messageIndex = responseBlockEnd;
        }
    }

    private void validateRuntimeMessage(Message message) {
        if (message == null || message.getRole() == null) {
            throw new IllegalArgumentException("提示词记忆消息及其角色不能为空");
        }
        if (message.getRole() == RoleType.TOOL
                && (message.getToolCallId() == null || message.getToolCallId().isBlank())) {
            throw new IllegalArgumentException("TOOL 消息必须携带非空 toolCallId");
        }
    }

    private void validatePersistedRow(PromptMemoryMessage row) {
        if (row == null || row.getRole() == null) {
            throw new IllegalArgumentException("持久化提示词记忆消息及其角色不能为空");
        }
        if (row.getRole() == RoleType.TOOL
                && (row.getToolCallId() == null || row.getToolCallId().isBlank())) {
            throw new IllegalArgumentException("持久化 TOOL 消息必须携带非空 toolCallId");
        }
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
