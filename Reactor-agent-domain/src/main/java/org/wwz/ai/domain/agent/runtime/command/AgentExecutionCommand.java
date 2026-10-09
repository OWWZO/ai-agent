package org.wwz.ai.domain.agent.runtime.command;

import com.alibaba.fastjson.JSONObject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;

import java.util.List;

/**
 * Agent runtime 的执行命令。
 * <p>该对象只描述一次运行需要的运行时输入，不承担 HTTP 请求或 SSE 响应职责。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentExecutionCommand {

    private String requestId;
    private String sessionId;
    private String userId;
    private String erp;
    private String query;
    private Integer agentType;
    private String outputStyle;
    private String basePrompt;
    private String sopPrompt;
    private String historyDialogue;
    private List<org.wwz.ai.domain.agent.runtime.dto.Message> workingMemoryMessages;
    private Boolean isStream;
    private List<Message> messages;
    private List<AgentExecutionFile> sessionFiles;
    private String model;
    private Boolean thinking;
    private String thinkingEffort;
    private String resumeQuestionId;
    private String resumeDesktopControlId;
    private String resumeApprovalId;
    private String resumeContextJson;
    private Boolean forcePlanMode;

    /**
     * 预装载到 Agent memory 的结构化消息。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        private String role;
        private String content;
        private String messageType;
        private List<ToolCall> toolCalls;
        private String toolCallId;
        private List<JSONObject> artifactRefs;
        private Boolean referenceOnly;
        private String commandCode;
        private List<AgentExecutionFile> uploadFile;
        private List<AgentExecutionFile> files;
    }
}
