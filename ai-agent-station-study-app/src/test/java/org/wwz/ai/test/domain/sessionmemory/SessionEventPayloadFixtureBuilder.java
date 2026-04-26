package org.wwz.ai.test.domain.sessionmemory;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessageEvent;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * 会话事件事实夹具构造器。
 */
public final class SessionEventPayloadFixtureBuilder {

    private SessionEventPayloadFixtureBuilder() {
    }

    public static AgentMessageEvent toolThoughtEvent(Long messageId,
                                                     int seqNo,
                                                     String toolUseId,
                                                     String toolName,
                                                     Object toolArguments,
                                                     String contentText,
                                                     String taskId,
                                                     Integer taskOrder) {
        return semanticAssistantThoughtEvent(
                messageId,
                seqNo,
                "tool",
                toolUseId,
                toolName,
                toolArguments,
                contentText,
                taskId,
                taskOrder);
    }

    public static AgentMessageEvent toolResultEvent(Long messageId,
                                                    int seqNo,
                                                    String eventType,
                                                    String eventSubType,
                                                    String toolUseId,
                                                    String toolName,
                                                    Object toolArguments,
                                                    String contentText,
                                                    String taskId,
                                                    Integer taskOrder,
                                                    List<JSONObject> artifactRefs) {
        return semanticToolResultEvent(
                messageId,
                seqNo,
                eventType,
                eventSubType,
                toolUseId,
                toolName,
                toolArguments,
                contentText,
                taskId,
                taskOrder,
                artifactRefs);
    }

    public static AgentMessageEvent semanticAssistantThoughtEvent(Long messageId,
                                                                  int seqNo,
                                                                  String thoughtType,
                                                                  String toolUseId,
                                                                  String toolName,
                                                                  Object toolArguments,
                                                                  String contentText,
                                                                  String taskId,
                                                                  Integer taskOrder) {
        return AgentMessageEvent.builder()
                .messageId(messageId)
                .seqNo(seqNo)
                .eventType("assistant_thought")
                .eventSubType(thoughtType)
                .displayArea("timeline")
                .taskId(taskId)
                .taskOrder(taskOrder)
                .toolUseId(toolUseId)
                .toolName(toolName)
                .toolArgumentsJson(toolArguments == null ? null : JSON.toJSONString(toolArguments))
                .contentText(contentText)
                .status("completed")
                .build();
    }

    public static AgentMessageEvent semanticToolUseEvent(Long messageId,
                                                         int seqNo,
                                                         String toolUseId,
                                                         String toolName,
                                                         Object toolArguments,
                                                         String taskId,
                                                         Integer taskOrder) {
        return AgentMessageEvent.builder()
                .messageId(messageId)
                .seqNo(seqNo)
                .eventType("tool_use")
                .eventSubType(resolveToolUseSubType(toolName))
                .displayArea("timeline")
                .taskId(taskId)
                .taskOrder(taskOrder)
                .toolUseId(toolUseId)
                .toolName(toolName)
                .toolArgumentsJson(toolArguments == null ? null : JSON.toJSONString(toolArguments))
                .contentText("准备调用 " + toolName)
                .status("completed")
                .build();
    }

    public static AgentMessageEvent semanticToolResultEvent(Long messageId,
                                                            int seqNo,
                                                            String sourceType,
                                                            String sourceSubType,
                                                            String toolUseId,
                                                            String toolName,
                                                            Object toolArguments,
                                                            String contentText,
                                                            String taskId,
                                                            Integer taskOrder,
                                                            List<JSONObject> artifactRefs) {
        JSONObject structuredData = new JSONObject(new LinkedHashMap<>());
        structuredData.put("messageType", sourceType);
        structuredData.put("answer", contentText);
        structuredData.put("isFinal", true);
        return AgentMessageEvent.builder()
                .messageId(messageId)
                .seqNo(seqNo)
                .eventType("tool_result")
                .eventSubType(resolveToolResultSubType(sourceType, sourceSubType))
                .displayArea(isWorkspaceResult(sourceType, sourceSubType) ? "workspace" : "timeline")
                .taskId(taskId)
                .taskOrder(taskOrder)
                .toolUseId(toolUseId)
                .toolName(toolName)
                .toolArgumentsJson(toolArguments == null ? null : JSON.toJSONString(toolArguments))
                .contentText(contentText)
                .referenceOnly(artifactRefs != null && !artifactRefs.isEmpty())
                .artifactRefsJson(artifactRefs == null || artifactRefs.isEmpty() ? null : JSON.toJSONString(artifactRefs))
                .structuredDataJson(structuredData.toJSONString())
                .status("completed")
                .build();
    }

    public static JSONObject artifactRef(String displayName, String url) {
        JSONObject artifactRef = new JSONObject(new LinkedHashMap<>());
        artifactRef.put("displayName", displayName);
        artifactRef.put("resourceKey", displayName);
        artifactRef.put("downloadUrl", url);
        artifactRef.put("previewUrl", url);
        artifactRef.put("missing", false);
        return artifactRef;
    }

    private static String resolveToolUseSubType(String toolName) {
        if (toolName == null) {
            return "tool";
        }
        return switch (toolName) {
            case "multimodalagent_tool" -> "knowledge";
            case "report_tool", "file_tool" -> "file_generation";
            default -> toolName.endsWith("_tool") ? toolName.substring(0, toolName.length() - 5) : toolName;
        };
    }

    private static String resolveToolResultSubType(String sourceType, String sourceSubType) {
        if ("deep_search".equals(sourceType)) {
            return "deep_search." + (sourceSubType == null ? "report" : sourceSubType);
        }
        return switch (sourceType) {
            case "browser" -> "browser.result";
            case "knowledge" -> "knowledge.answer";
            case "markdown" -> "markdown.report";
            case "html" -> "html.page";
            case "ppt" -> "ppt.deck";
            case "code" -> "code.bundle";
            case "file" -> "file.output";
            case "data_analysis" -> "data_analysis.output";
            default -> sourceType + "." + (sourceSubType == null ? "result" : sourceSubType);
        };
    }

    private static boolean isWorkspaceResult(String sourceType, String sourceSubType) {
        return switch (sourceType) {
            case "browser", "knowledge", "markdown", "html", "ppt", "code", "file", "data_analysis" -> true;
            case "deep_search" -> "report".equalsIgnoreCase(sourceSubType);
            default -> false;
        };
    }
}
