package org.wwz.ai.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 执行命令实体
 * 2025/7/27 16:46
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ExecuteCommandEntity {

    private String aiAgentId;

    private String message;

    private String sessionId;

    private Integer maxStep;

    /**
     * 请求ID，用于追踪请求
     */
    private String requestId;

    /**
     * 智能体类型
     */
    private Integer agentType;

    /**
     * 输出样式
     */
    private String outputStyle;

    /**
     * SOP提示词
     */
    private String sopPrompt;

    /**
     * 基础提示词
     */
    private String basePrompt;

    /**
     * 是否流式输出
     */
    private Boolean isStream;

    /**
     * 模板类型
     */
    private String templateType;

    /**
     * 执行策略 (可选，若不为空则优先使用，否则根据aiAgentId查询)
     */
    private String strategy;

}
