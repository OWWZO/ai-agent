package org.wwz.ai.domain.agent.reactor.model.req;


import com.alibaba.fastjson.JSONObject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
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
    /**
     * 匿名访客ID。
     */
    private String visitorId;
    private String erp;
    private String query;
    private Integer agentType;
    /** 独立问数链路标记；主 Agent 只由 agentType 区分 ReAct/PlanSolve。 */
    private String outputStyle;
    private String basePrompt;
    private String sopPrompt;
    /**
     * 会话级历史摘要文本（legacy/debug，默认不进 LLM）。
     */
    private String historyDialogue;
    /**
     * 跨轮工作记忆消息链（ledger hydrate），主路径进 Memory.preload。
     */
    private List<org.wwz.ai.domain.agent.runtime.dto.Message> workingMemoryMessages;
    private Boolean isStream;
    private List<Message> messages;
    /**
     * 恢复出的会话级稳定文件
     */
    private List<FileInformation> sessionFiles;
    /**
     * 本轮模型引用：modelId 或上游 modelName；空则从 MySQL 启用模型中选择默认项。
     * 由 LlmModelCatalog 解析 DB 配置，支持前端热切换。
     */
    private String model;
    /** 是否开启深度思考（本轮覆盖） */
    private Boolean thinking;
    /** 思考档位 low|medium|high */
    private String thinkingEffort;
    /**
     * AskUserQuestion continuation：非空表示本轮是回答后续跑，query 应为空，
     * 由策略在 hydrate 后追加对应 tool observation。
     */
    private String resumeQuestionId;
    /**
     * ExitPlanMode continuation：非空表示本轮是计划审批后续跑，query 应为空，
     * 由策略在 hydrate 后追加 ExitPlanMode tool observation。
     */
    private String resumeApprovalId;
    /** 续跑瘦快照 JSON（PlanMode / agent 配置），Prepare 时恢复 */
    private String resumeContextJson;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        private String role;
        private String content;
        /**
         * 结构化消息类型，区分 thought / tool_use / tool_result / artifact 等上下文块。
         */
        private String messageType;
        /**
         * 工具调用链，映射到内部 assistant tool_calls 语义。
         */
        private List<ToolCall> toolCalls;
        /**
         * 工具结果对应的 toolCallId，映射到内部 tool 消息。
         */
        private String toolCallId;
        /**
         * 稳定产物引用，供节点和工具链复用。
         */
        private List<JSONObject> artifactRefs;
        /**
         * 是否只保留摘要或引用，不直接内联正文。
         */
        private Boolean referenceOnly;
        private String commandCode;
        private List<FileInformation> uploadFile;
        private List<FileInformation> files;

    }
}
