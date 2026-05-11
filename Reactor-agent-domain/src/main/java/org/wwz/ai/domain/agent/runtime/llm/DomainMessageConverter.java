package org.wwz.ai.domain.agent.runtime.llm;

import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.runtime.util.StringUtil;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;

import javax.annotation.Resource;
import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将领域 Message 转换为 Spring AI Message。
 * 对 TOOL 消息采用两段式转换：先扫描 assistant tool calls，再恢复 toolCallId -> toolName。
 */
@Component
public class DomainMessageConverter {

    @Resource
    private ReactorConfig reactorConfig;

    /**
     * 按原始顺序转换整个对话历史。
     */
    public List<org.springframework.ai.chat.messages.Message> convert(List<Message> messages) {
        List<org.springframework.ai.chat.messages.Message> convertedMessages = new ArrayList<>();
        Map<String, String> toolCallNameIndex = new LinkedHashMap<>();
        if (messages == null || messages.isEmpty()) {
            return convertedMessages;
        }

        for (Message message : messages) {
            if (message == null || message.getRole() == null) {
                continue;
            }
            org.springframework.ai.chat.messages.Message converted = convertMessage(message, toolCallNameIndex);
            if (converted != null) {
                convertedMessages.add(converted);
            }
            if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
                for (ToolCall toolCall : message.getToolCalls()) {
                    if (toolCall != null && toolCall.getFunction() != null
                            && StringUtils.isNotBlank(toolCall.getId())
                            && StringUtils.isNotBlank(toolCall.getFunction().getName())) {
                        toolCallNameIndex.put(toolCall.getId(), toolCall.getFunction().getName());
                    }
                }
            }
        }
        return convertedMessages;
    }

    private org.springframework.ai.chat.messages.Message convertMessage(Message message, Map<String, String> toolCallNameIndex) {
        return switch (message.getRole()) {
            case SYSTEM -> new SystemMessage(StringUtils.defaultString(message.getContent()));
            case USER -> toUserMessage(message);
            case ASSISTANT -> toAssistantMessage(message);
            case TOOL -> toToolResponseMessage(message, toolCallNameIndex);
            default -> throw new IllegalArgumentException("Unsupported message role: " + message.getRole());
        };
    }

    private UserMessage toUserMessage(Message message) {
        if (StringUtils.isBlank(message.getBase64Image())) {
            return new UserMessage(StringUtils.defaultString(message.getContent()));
        }
        Media media = buildMedia(message.getBase64Image());
        return UserMessage.builder()
                .text(StringUtils.defaultString(message.getContent()))
                .media(media)
                .build();
    }

    private AssistantMessage toAssistantMessage(Message message) {
        AssistantMessage.Builder builder = AssistantMessage.builder()
                .content(StringUtils.defaultString(message.getContent()))
                .properties(Map.of());

        if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
            List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();
            for (ToolCall toolCall : message.getToolCalls()) {
                if (toolCall == null || toolCall.getFunction() == null) {
                    continue;
                }
                toolCalls.add(new AssistantMessage.ToolCall(
                        toolCall.getId(),
                        toolCall.getType(),
                        toolCall.getFunction().getName(),
                        toolCall.getFunction().getArguments()));
            }
            builder.toolCalls(toolCalls);
        }

        if (StringUtils.isNotBlank(message.getBase64Image())) {
            builder.media(List.of(buildMedia(message.getBase64Image())));
        }
        return builder.build();
    }

    /**
     * TOOL 消息需要补齐 toolName，否则 Spring AI 无法正确回放工具结果。
     */
    private ToolResponseMessage toToolResponseMessage(Message message, Map<String, String> toolCallNameIndex) {
        String toolCallId = StringUtils.trimToEmpty(message.getToolCallId());
        String toolName = toolCallNameIndex.get(toolCallId);
        if (StringUtils.isBlank(toolName)) {
            throw new IllegalStateException("Cannot resolve tool name for toolCallId: " + toolCallId);
        }

        String content = StringUtil.textDesensitization(
                StringUtils.defaultString(message.getContent()),
                reactorConfig.getSensitivePatterns());

        ToolResponseMessage.ToolResponse response = new ToolResponseMessage.ToolResponse(
                toolCallId,
                toolName,
                content
        );
        return ToolResponseMessage.builder()
                .responses(List.of(response))
                .metadata(Map.of(AbstractMessage.MESSAGE_TYPE, MessageType.TOOL.getValue()))
                .build();
    }

    private Media buildMedia(String rawBase64Image) {
        String normalized = rawBase64Image.trim();
        MimeType mimeType = MimeTypeUtils.IMAGE_JPEG;

        if (normalized.startsWith("data:")) {
            int commaIndex = normalized.indexOf(',');
            String metadata = commaIndex > 0 ? normalized.substring(5, commaIndex) : "";
            String mimeTypeValue = metadata.split(";")[0];
            if (StringUtils.isNotBlank(mimeTypeValue)) {
                mimeType = MimeType.valueOf(mimeTypeValue);
            }
            normalized = commaIndex > 0 ? normalized.substring(commaIndex + 1) : normalized;
        }

        byte[] data = Base64.getDecoder().decode(normalized);
        return new Media(mimeType, new ByteArrayResource(data));
    }
}
