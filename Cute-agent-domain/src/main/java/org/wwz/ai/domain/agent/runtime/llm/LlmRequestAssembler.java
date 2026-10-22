package org.wwz.ai.domain.agent.runtime.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.tool.mcp.model.McpToolInfo;
import org.wwz.ai.domain.agent.runtime.util.ToolSchemaNormalizer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 出站请求装配：domain Message → {@link LlmRequest}，工具集合 → {@link LlmToolDefinition}。
 * <p>
 * 只做结构转换，不碰网络、账本和观测。
 */
final class LlmRequestAssembler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    LlmRequest build(AgentContext context,
                     String model,
                     LLMSettings settings,
                     List<Message> domainMessages,
                     List<LlmToolDefinition> tools,
                     String toolChoice,
                     Double overrideTemperature) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (context != null) {
            putIfNotNull(metadata, "requestId", context.getRequestId());
            putIfNotNull(metadata, "sessionId", context.getSessionId());
            if (context.getAgentRunState() != null) {
                putIfNotNull(metadata, "agentName", context.getAgentRunState().getCurrentAgentName());
                putIfNotNull(metadata, "stepNo", context.getAgentRunState().getCurrentStepNo());
            }
        }
        return LlmRequest.builder()
                .model(model)
                .settings(settings)
                .messages(toLlmMessages(domainMessages))
                .tools(tools == null || tools.isEmpty() ? List.of() : List.copyOf(tools))
                .toolChoice(toolChoice)
                .temperature(overrideTemperature)
                .sessionId(context == null ? null : context.getSessionId())
                .metadata(metadata)
                .build();
    }

    private List<LlmMessage> toLlmMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<LlmMessage> result = new ArrayList<>();
        for (Message message : messages) {
            if (message == null) {
                continue;
            }
            List<LlmToolCall> toolCalls = new ArrayList<>();
            if (message.getToolCalls() != null) {
                for (ToolCall toolCall : message.getToolCalls()) {
                    if (toolCall == null || toolCall.getFunction() == null) {
                        continue;
                    }
                    toolCalls.add(LlmToolCall.builder()
                            .id(toolCall.getId())
                            .type(toolCall.getType())
                            .name(toolCall.getFunction().getName())
                            .arguments(toolCall.getFunction().getArguments())
                            .build());
                }
            }
            result.add(LlmMessage.builder()
                    .role(message.getRole())
                    .content(message.getContent())
                    .reasoningContent(message.getReasoningContent())
                    .base64Image(message.getBase64Image())
                    .toolCallId(message.getToolCallId())
                    .toolCalls(toolCalls)
                    .build());
        }
        return result;
    }

    /**
     * 本地工具与 MCP 工具统一转成出站工具定义。
     * <p>
     * 排序按名称固定，避免同一批工具每次下发顺序不同破坏 prompt cache。
     */
    List<LlmToolDefinition> buildToolDefinitions(ToolCollection tools) {
        if (tools == null) {
            return List.of();
        }
        List<LlmToolDefinition> definitions = new ArrayList<>();
        List<BaseTool> localTools = tools.getToolMap() == null
                ? List.of()
                : tools.getToolMap().values().stream()
                .filter(tool -> tool != null && StringUtils.isNotBlank(tool.getName()))
                .sorted(Comparator.comparing(BaseTool::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        for (BaseTool tool : localTools) {
            LlmToolDefinition definition = ToolDefinitionCache.getOrCreateFromMap(
                    tool.getName(), StringUtils.defaultString(tool.getDescription()), tool.toParams());
            definitions.add(definition.withInvoker(input -> tool.execute(parseToolInput(input))));
        }

        List<McpToolInfo> mcpTools = tools.getMcpToolMap() == null
                ? List.of()
                : tools.getMcpToolMap().values().stream()
                .filter(tool -> tool != null && StringUtils.isNotBlank(tool.getName()))
                .sorted(Comparator.comparing(McpToolInfo::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        for (McpToolInfo tool : mcpTools) {
            LlmToolDefinition definition = ToolDefinitionCache.getOrCreateFromRawSchemaString(
                    tool.getName(), StringUtils.defaultString(tool.getDesc()), tool.getParameters());
            definitions.add(definition.withInvoker(input -> tools.execute(tool.getName(), input)));
        }
        return definitions;
    }

    private Object parseToolInput(String toolInput) {
        if (StringUtils.isBlank(toolInput)) {
            return new LinkedHashMap<String, Object>();
        }
        try {
            return objectMapper.readValue(toolInput, Object.class);
        } catch (Exception ignored) {
            return toolInput;
        }
    }

    private static void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    /** system 段 + 对话段合并成一条有序消息列表，跳过 null 元素。 */
    static List<Message> merge(List<Message> systemMsgs, List<Message> messages) {
        List<Message> merged = new ArrayList<>();
        if (systemMsgs != null) {
            for (Message systemMsg : systemMsgs) {
                if (systemMsg != null) {
                    merged.add(systemMsg);
                }
            }
        }
        if (messages != null) {
            for (Message message : messages) {
                if (message != null) {
                    merged.add(message);
                }
            }
        }
        return merged;
    }

    /** 单条 system + 对话段。 */
    static List<Message> merge(Message systemMsg, List<Message> messages) {
        List<Message> merged = new ArrayList<>();
        if (systemMsg != null) {
            merged.add(systemMsg);
        }
        if (messages != null) {
            for (Message message : messages) {
                if (message != null) {
                    merged.add(message);
                }
            }
        }
        return merged;
    }
}
