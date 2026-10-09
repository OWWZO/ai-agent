package org.wwz.ai.infrastructure.llm.springai;

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
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.runtime.enums.RoleType;
import org.wwz.ai.domain.agent.runtime.llm.LlmMessage;
import org.wwz.ai.domain.agent.runtime.llm.LlmToolCall;
import org.wwz.ai.domain.agent.runtime.llm.ReasoningContentExtractor;
import org.wwz.ai.domain.agent.runtime.util.StringUtil;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Domain Message 到 Spring AI Message 的防腐层转换。
 */
@Component
public class SpringAiMessageMapper {

    @Resource
    private ReactorConfig reactorConfig;

    public List<org.springframework.ai.chat.messages.Message> convert(List<LlmMessage> messages) {
        List<org.springframework.ai.chat.messages.Message> convertedMessages = new ArrayList<>();
        Map<String, String> toolCallNameIndex = new LinkedHashMap<>();
        if (messages == null || messages.isEmpty()) {
            return convertedMessages;
        }

        for (LlmMessage message : messages) {
            if (message == null || message.getRole() == null) {
                continue;
            }
            if (message.getRole() == RoleType.TOOL) {
                String toolCallId = StringUtils.trimToEmpty(message.getToolCallId());
                if (StringUtils.isBlank(toolCallNameIndex.get(toolCallId))) {
                    continue;
                }
                convertedMessages.add(toToolResponseMessage(message, toolCallNameIndex));
                if (StringUtils.isNotBlank(message.getBase64Image())) {
                    convertedMessages.add(toToolImageUserMessage(message, toolCallNameIndex));
                }
            } else {
                org.springframework.ai.chat.messages.Message converted = convertMessage(message);
                if (converted != null) {
                    convertedMessages.add(converted);
                }
            }
            indexAssistantToolCalls(message, toolCallNameIndex);
        }
        return convertedMessages;
    }

    private org.springframework.ai.chat.messages.Message convertMessage(LlmMessage message) {
        return switch (message.getRole()) {
            case SYSTEM -> new SystemMessage(StringUtils.defaultString(message.getContent()));
            case USER -> toUserMessage(message);
            case ASSISTANT -> toAssistantMessage(message);
            case TOOL -> null;
            default -> throw new IllegalArgumentException("Unsupported message role: " + message.getRole());
        };
    }

    private UserMessage toUserMessage(LlmMessage message) {
        if (StringUtils.isBlank(message.getBase64Image())) {
            return new UserMessage(StringUtils.defaultString(message.getContent()));
        }
        return UserMessage.builder()
                .text(StringUtils.defaultString(message.getContent()))
                .media(buildMedia(message.getBase64Image()))
                .build();
    }

    private AssistantMessage toAssistantMessage(LlmMessage message) {
        Map<String, Object> properties = new LinkedHashMap<>();
        if (StringUtils.isNotBlank(message.getReasoningContent())) {
            properties.put(ReasoningContentExtractor.METADATA_REASONING_CONTENT, message.getReasoningContent());
        }
        AssistantMessage.Builder builder = AssistantMessage.builder()
                .content(StringUtils.defaultString(message.getContent()))
                .properties(properties);
        if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
            List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();
            for (LlmToolCall toolCall : message.getToolCalls()) {
                if (toolCall == null) {
                    continue;
                }
                toolCalls.add(new AssistantMessage.ToolCall(
                        toolCall.getId(),
                        toolCall.getType(),
                        toolCall.getName(),
                        toolCall.getArguments()));
            }
            builder.toolCalls(toolCalls);
        }
        if (StringUtils.isNotBlank(message.getBase64Image())) {
            builder.media(List.of(buildMedia(message.getBase64Image())));
        }
        return builder.build();
    }

    private ToolResponseMessage toToolResponseMessage(LlmMessage message, Map<String, String> toolCallNameIndex) {
        String toolCallId = StringUtils.trimToEmpty(message.getToolCallId());
        String toolName = StringUtils.defaultIfBlank(toolCallNameIndex.get(toolCallId), "tool");
        String content = StringUtil.textDesensitization(
                StringUtils.defaultString(message.getContent()), reactorConfig.getSensitivePatterns());
        ToolResponseMessage.ToolResponse response = new ToolResponseMessage.ToolResponse(toolCallId, toolName, content);
        return ToolResponseMessage.builder()
                .responses(List.of(response))
                .metadata(Map.of(AbstractMessage.MESSAGE_TYPE, MessageType.TOOL.getValue()))
                .build();
    }

    private UserMessage toToolImageUserMessage(LlmMessage message, Map<String, String> toolCallNameIndex) {
        String toolCallId = StringUtils.trimToEmpty(message.getToolCallId());
        String toolName = toolCallNameIndex.getOrDefault(toolCallId, "tool");
        Media media = buildMedia(message.getBase64Image());
        return UserMessage.builder()
                .text("[tool image] tool=" + toolName + " tool_call_id=" + toolCallId + " mime=" + media.getMimeType())
                .media(media)
                .build();
    }

    private void indexAssistantToolCalls(LlmMessage message, Map<String, String> toolCallNameIndex) {
        if (message.getToolCalls() == null || message.getToolCalls().isEmpty()) {
            return;
        }
        for (LlmToolCall toolCall : message.getToolCalls()) {
            if (toolCall == null || StringUtils.isBlank(toolCall.getId())) {
                continue;
            }
            toolCallNameIndex.put(toolCall.getId(), StringUtils.defaultIfBlank(toolCall.getName(), "tool"));
        }
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
        return new Media(mimeType, new ByteArrayResource(Base64.getDecoder().decode(normalized)));
    }
}
