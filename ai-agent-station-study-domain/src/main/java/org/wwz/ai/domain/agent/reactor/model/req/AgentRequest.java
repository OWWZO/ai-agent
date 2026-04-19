package org.wwz.ai.domain.agent.reactor.model.req;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.wwz.ai.domain.agent.reactor.model.dto.FileInformation;

import java.util.List;

/**
 * Assistant请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRequest {
    private String requestId;
    /**
     * 会话ID，用于多轮对话上下文复用
     */
    private String sessionId;
    private String erp;
    private String query;
    private Integer agentType;
    private String basePrompt;
    private String sopPrompt;
    /**
     * 会话级历史摘要文本
     */
    private String historyDialogue;
    private Boolean isStream;
    private List<Message> messages;
    /**
     * 恢复出的会话级稳定文件
     */
    private List<FileInformation> sessionFiles;
    private String outputStyle; // 交付物产出格式：html(网页模式）， docs(文档模式）， table(表格模式）
    private String aiAgentId;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        private String role;
        private String content;
        private String commandCode;
        private List<FileInformation> uploadFile;
        private List<FileInformation> files;

    }
}
