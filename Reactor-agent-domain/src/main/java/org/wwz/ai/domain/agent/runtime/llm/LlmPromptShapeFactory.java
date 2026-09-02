package org.wwz.ai.domain.agent.runtime.llm;

import com.alibaba.fastjson.JSON;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.dto.tool.McpToolInfo;
import org.wwz.ai.domain.agent.runtime.planmode.PlanModeToolPolicy;
import org.wwz.ai.domain.agent.runtime.prompt.AgentPrompt;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.util.ToolSchemaNormalizer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 与 LLM 实际发送请求共用的 prompt shape 构造。
 */
public final class LlmPromptShapeFactory {

    private LlmPromptShapeFactory() {
    }

    public static LlmAskToolProtocol protocolOf(String functionCallType) {
        return "struct_parse".equals(functionCallType)
                ? LlmAskToolProtocol.STRUCT_PARSE
                : LlmAskToolProtocol.FUNCTION_CALL;
    }

    public static PromptShape forText(Message systemMessage, List<Message> messages) {
        return PromptShape.text(systemMessage, messages);
    }

    public static PromptShape forAskTool(AgentContext context,
                                         Message systemMessage,
                                         List<Message> messages,
                                         ToolCollection tools,
                                         LlmAskToolProtocol protocol) {
        ToolCollection filtered = PlanModeToolPolicy.filterTools(context, tools);
        LlmAskToolProtocol effective = protocol == null ? LlmAskToolProtocol.FUNCTION_CALL : protocol;
        if (effective == LlmAskToolProtocol.STRUCT_PARSE) {
            return PromptShape.structParse(
                    buildStructParseSystemMessage(systemMessage, filtered),
                    messages,
                    filtered);
        }
        if (effective == LlmAskToolProtocol.TEXT) {
            return PromptShape.text(systemMessage, messages);
        }
        return PromptShape.functionCall(systemMessage, messages, filtered);
    }

    public static Message buildStructParseSystemMessage(Message systemMsg, ToolCollection tools) {
        String toolPrompt = buildStructParseToolPrompt(tools);
        String originalSystemPrompt = systemMsg != null ? StringUtils.defaultString(systemMsg.getContent()) : "";
        String mergedContent = StringUtils.isBlank(originalSystemPrompt)
                ? toolPrompt
                : originalSystemPrompt + "\n" + toolPrompt;
        return Message.systemMessage(mergedContent, null);
    }

    public static String buildStructParseToolPrompt(ToolCollection tools) {
        StringBuilder prompt = new StringBuilder(AgentPrompt.STRUCT_PARSE_TOOL_SYSTEM_PROMPT);
        if (prompt.length() > 0) {
            prompt.append('\n');
        }
        if (tools == null) {
            return prompt.toString();
        }
        if (tools.getToolMap() != null) {
            for (BaseTool tool : tools.getToolMap().values()) {
                if (tool == null) {
                    continue;
                }
                Map<String, Object> functionMap = new LinkedHashMap<>();
                functionMap.put("name", tool.getName());
                functionMap.put("description", tool.getDescription());
                functionMap.put("parameters",
                        addFunctionNameParam(
                                ToolSchemaNormalizer.normalizeSchema(tool.toParams(), tool.getName()),
                                tool.getName()));
                prompt.append(String.format("- `%s`%n```json %s ```%n",
                        tool.getName(), JSON.toJSONString(functionMap)));
            }
        }
        if (tools.getMcpToolMap() != null) {
            for (McpToolInfo tool : tools.getMcpToolMap().values()) {
                if (tool == null) {
                    continue;
                }
                Map<String, Object> functionMap = new LinkedHashMap<>();
                functionMap.put("name", tool.getName());
                functionMap.put("description", tool.getDesc());
                functionMap.put("parameters",
                        addFunctionNameParam(
                                ToolSchemaNormalizer.normalizeSchemaAsMap(tool.getParameters(), tool.getName()),
                                tool.getName()));
                prompt.append(String.format("- `%s`%n```json %s ```%n",
                        tool.getName(), JSON.toJSONString(functionMap)));
            }
        }
        return prompt.toString();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> addFunctionNameParam(Map<String, Object> parameters, String toolName) {
        Map<String, Object> newParameters = new LinkedHashMap<>(parameters == null ? Map.of() : parameters);
        ArrayList<String> newRequired = new ArrayList<>();
        newRequired.add("function_name");
        if (parameters != null && parameters.containsKey("required") && parameters.get("required") != null) {
            newRequired.addAll((List<String>) parameters.get("required"));
        }
        newParameters.put("required", newRequired);

        Map<String, Object> newProperties = new LinkedHashMap<>();
        Map<String, Object> functionNameMap = new HashMap<>();
        functionNameMap.put("description", "默认值为工具名: " + toolName);
        functionNameMap.put("type", "string");
        newProperties.put("function_name", functionNameMap);
        if (parameters != null && parameters.containsKey("properties") && parameters.get("properties") != null) {
            newProperties.putAll((Map<String, Object>) parameters.get("properties"));
        }
        newParameters.put("properties", newProperties);
        return newParameters;
    }
}
