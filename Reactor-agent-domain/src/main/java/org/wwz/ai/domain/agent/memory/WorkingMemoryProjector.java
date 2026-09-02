package org.wwz.ai.domain.agent.memory;

import com.alibaba.fastjson.JSON;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.runtime.enums.RoleType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Memory Message 与 working_memory 行表互转。
 */
public class WorkingMemoryProjector {

    public List<WorkingMemoryMessage> project(List<Message> messages, String sessionId, String requestId, Long runId) {
        return project(messages, sessionId, WorkingMemoryScopes.MAIN, requestId, runId);
    }

    public List<WorkingMemoryMessage> project(List<Message> messages,
                                              String sessionId,
                                              String memoryScope,
                                              String requestId,
                                              Long runId) {
        // working_memory 是跨轮 prompt 的投影，不是历史回放账本；这里只保存下一轮 hydrate 所需字段。
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        String scope = WorkingMemoryScopes.normalize(memoryScope);
        List<WorkingMemoryMessage> rows = new ArrayList<>(messages.size());
        int seq = 0;
        for (Message message : messages) {
            if (message == null || message.getRole() == null) {
                continue;
            }
            // session_env 必须入库：跨轮 messages 前缀续写依赖首轮 env
            rows.add(WorkingMemoryMessage.builder()
                    .sessionId(sessionId)
                    .memoryScope(scope)
                    .requestId(requestId)
                    .originMessageKey(StringUtils.defaultIfBlank(message.getOriginMessageKey(),
                            StringUtils.defaultString(requestId) + ":" + seq))
                    .runId(runId)
                    .seqNo(seq++)
                    .role(message.getRole().name())
                    .content(message.getContent())
                    .reasoningContent(message.getReasoningContent())
                    .toolCallId(message.getToolCallId())
                    .toolCallsJson(message.getToolCalls() == null || message.getToolCalls().isEmpty()
                            ? null
                            : JSON.toJSONString(message.getToolCalls()))
                    .base64Image(message.getBase64Image())
                    .messageKind(resolveMessageKind(message))
                    .visibility("ALL")
                    .tokenEstimate(0)
                    .deleted(0)
                    .build());
        }
        return rows;
    }

    public List<Message> hydrate(List<WorkingMemoryMessage> rows) {
        // 行表按 sequence 恢复为 Memory 消息，未知 kind 仍保留正文，避免投影升级导致历史内容丢失。
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<Message> messages = new ArrayList<>(rows.size());
        for (WorkingMemoryMessage row : rows) {
            if (row == null || StringUtils.isBlank(row.getRole())) {
                continue;
            }
            RoleType role = parseRole(row.getRole());
            List<ToolCall> toolCalls = parseToolCalls(row.getToolCallsJson());
            if (role == RoleType.TOOL) {
                messages.add(Message.toolMessage(row.getContent(), row.getToolCallId(), row.getBase64Image()));
            } else if (role == RoleType.ASSISTANT && toolCalls != null && !toolCalls.isEmpty()) {
                messages.add(Message.fromToolCalls(row.getContent(), row.getReasoningContent(), toolCalls));
            } else if (role == RoleType.ASSISTANT) {
                messages.add(Message.assistantMessage(row.getContent(), row.getReasoningContent(), row.getBase64Image()));
            } else if (role == RoleType.SYSTEM) {
                messages.add(Message.systemMessage(row.getContent(), row.getBase64Image()));
            } else {
                messages.add(Message.userMessage(row.getContent(), row.getBase64Image()));
            }
        }
        return messages;
    }

    private String resolveMessageKind(Message message) {
        if (message.getRole() == RoleType.USER) {
            return "query";
        }
        if (message.getRole() == RoleType.TOOL) {
            return "tool_observation";
        }
        if (message.getRole() == RoleType.ASSISTANT
                && message.getToolCalls() != null
                && !message.getToolCalls().isEmpty()) {
            return "assistant";
        }
        if (message.getRole() == RoleType.ASSISTANT) {
            return "assistant";
        }
        return "system_note";
    }

    private RoleType parseRole(String role) {
        try {
            return RoleType.valueOf(role.trim().toUpperCase());
        } catch (Exception e) {
            return RoleType.USER;
        }
    }

    @SuppressWarnings("unchecked")
    private List<ToolCall> parseToolCalls(String json) {
        if (StringUtils.isBlank(json)) {
            return Collections.emptyList();
        }
        try {
            List<ToolCall> parsed = JSON.parseArray(json, ToolCall.class);
            return parsed == null ? Collections.emptyList() : parsed;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
