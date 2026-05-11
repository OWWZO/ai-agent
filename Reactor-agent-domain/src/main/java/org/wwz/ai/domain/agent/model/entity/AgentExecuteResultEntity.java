package org.wwz.ai.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * AutoAgent 执行结果实体
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentExecuteResultEntity {

    /**
     * 数据类型：analysis(分析阶段), execution(执行阶段), supervision(监督阶段), summary(总结阶段), error(错误信息), complete(完成标识)
     * 细分类型：analysis_status(任务状态分析), analysis_history(执行历史评估), analysis_strategy(下一步策略), analysis_progress(完成度评估)
     *          execution_target(执行目标), execution_process(执行过程), execution_result(执行结果), execution_quality(质量检查)
     *          supervision_assessment(质量评估), supervision_issues(问题识别), supervision_suggestions(改进建议), supervision_score(质量评分)
     */
    private String type;

    /**
     * 子类型标识，用于前端细粒度展示
     */
    private String subType;

    /**
     * 当前步骤(大步骤 1-4)
     */
    private Integer step;

    /**
     * 步骤名称，用于前端展示：MCP工具分析/执行规划/解析步骤/执行步骤
     */
    private String stepName;

    /**
     * 数据内容
     */
    private String content;

    /**
     * 是否完成
     */
    private Boolean completed;

    /**
     * 时间戳
     */
    private Long timestamp;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 执行指标（仅 complete 类型携带）
     */
    private LlmMetrics metrics;

    /**
     * 流式输出：是否为流开始（前端创建新消息占位）
     */
    private Boolean streamStart;

    /**
     * 流式输出：是否为增量内容（前端追加到当前消息）
     */
    private Boolean streamDelta;

    /**
     * 流式输出：是否为流结束（前端结束当前流式消息）
     */
    private Boolean streamEnd;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class LlmMetrics {
        /** 输入 token 总数 */
        private Long inputTokens;
        /** 输出 token 总数 */
        private Long outputTokens;
        /** 总 token 数 */
        private Long totalTokens;
        /** 预估成本（元） */
        private BigDecimal estimatedCost;
        /** 总耗时（毫秒） */
        private Long totalDurationMs;
        /** LLM 调用次数 */
        private Integer callCount;
    }

    /** 大步骤名称映射 */
    public static final String[] STEP_NAMES = {"", "MCP工具分析", "执行规划", "解析步骤", "执行步骤"};

    /**
     * 创建分析阶段结果
     */
    public static AgentExecuteResultEntity createAnalysisResult(Integer step, String content, String sessionId) {
        return AgentExecuteResultEntity.builder()
                .type("analysis")
                .step(step)
                .stepName(step != null && step >= 1 && step < STEP_NAMES.length ? STEP_NAMES[step] : null)
                .content(content)
                .completed(false)
                .timestamp(System.currentTimeMillis())
                .sessionId(sessionId)
                .build();
    }

    /**
     * 创建分析阶段细分结果
     */
    public static AgentExecuteResultEntity createAnalysisSubResult(Integer step, String subType, String content, String sessionId) {
        return AgentExecuteResultEntity.builder()
                .type("analysis")
                .subType(subType)
                .step(step)
                .stepName(step != null && step >= 1 && step < STEP_NAMES.length ? STEP_NAMES[step] : null)
                .content(content)
                .completed(false)
                .timestamp(System.currentTimeMillis())
                .sessionId(sessionId)
                .build();
    }

    /**
     * 创建执行阶段结果
     */
    public static AgentExecuteResultEntity createExecutionResult(Integer step, String content, String sessionId) {
        return AgentExecuteResultEntity.builder()
                .type("execution")
                .step(step)
                .stepName(step != null && step >= 1 && step < STEP_NAMES.length ? STEP_NAMES[step] : null)
                .content(content)
                .completed(false)
                .timestamp(System.currentTimeMillis())
                .sessionId(sessionId)
                .build();
    }

    /**
     * 创建执行阶段细分结果
     */
    public static AgentExecuteResultEntity createExecutionSubResult(Integer step, String subType, String content, String sessionId) {
        return AgentExecuteResultEntity.builder()
                .type("execution")
                .subType(subType)
                .step(step)
                .content(content)
                .completed(false)
                .timestamp(System.currentTimeMillis())
                .sessionId(sessionId)
                .build();
    }

    /**
     * 创建监督阶段结果
     */
    public static AgentExecuteResultEntity createSupervisionResult(Integer step, String content, String sessionId) {
        return AgentExecuteResultEntity.builder()
                .type("supervision")
                .step(step)
                .content(content)
                .completed(false)
                .timestamp(System.currentTimeMillis())
                .sessionId(sessionId)
                .build();
    }

    /**
     * 创建监督阶段细分结果
     */
    public static AgentExecuteResultEntity createSupervisionSubResult(Integer step, String subType, String content, String sessionId) {
        return AgentExecuteResultEntity.builder()
                .type("supervision")
                .subType(subType)
                .step(step)
                .content(content)
                .completed(false)
                .timestamp(System.currentTimeMillis())
                .sessionId(sessionId)
                .build();
    }

    /**
     * 创建总结阶段细分的结果
     */
    public static AgentExecuteResultEntity createSummarySubResult(String subType, String content, String sessionId) {
        return AgentExecuteResultEntity.builder()
                .type("summary")
                .subType(subType)
                .step(4)
                .content(content)
                .completed(false)
                .timestamp(System.currentTimeMillis())
                .sessionId(sessionId)
                .build();
    }

    /**
     * 创建总结阶段结果
     */
    public static AgentExecuteResultEntity createSummaryResult(String content, String sessionId) {
        return AgentExecuteResultEntity.builder()
                .type("summary")
                .step(null)
                .content(content)
                .completed(true)
                .timestamp(System.currentTimeMillis())
                .sessionId(sessionId)
                .build();
    }

    /**
     * 创建错误结果
     */
    public static AgentExecuteResultEntity createErrorResult(String content, String sessionId) {
        return AgentExecuteResultEntity.builder()
                .type("error")
                .step(null)
                .content(content)
                .completed(true)
                .timestamp(System.currentTimeMillis())
                .sessionId(sessionId)
                .build();
    }

    /**
     * 创建完成标识（无指标）
     */
    public static AgentExecuteResultEntity createCompleteResult(String sessionId) {
        return AgentExecuteResultEntity.builder()
                .type("complete")
                .step(null)
                .content(null)
                .completed(true)
                .timestamp(System.currentTimeMillis())
                .sessionId(sessionId)
                .build();
    }

    /**
     * 创建完成标识（带指标）
     */
    public static AgentExecuteResultEntity createCompleteResult(String sessionId, LlmMetrics metrics) {
        return AgentExecuteResultEntity.builder()
                .type("complete")
                .step(null)
                .content(null)
                .completed(true)
                .timestamp(System.currentTimeMillis())
                .sessionId(sessionId)
                .metrics(metrics)
                .build();
    }

    /**
     * 流式开始：前端创建新消息占位
     */
    public static AgentExecuteResultEntity createStreamStart(Integer step, String stepName, String subType, String sessionId) {
        return AgentExecuteResultEntity.builder()
                .type("分析")
                .subType(subType)
                .step(step)
                .stepName(step != null && step >= 1 && step < STEP_NAMES.length ? STEP_NAMES[step] : null)
                .content("")
                .streamStart(true)
                .timestamp(System.currentTimeMillis())
                .sessionId(sessionId)
                .build();
    }

    /**
     * 流式增量：前端追加 content 到当前消息
     */
    public static AgentExecuteResultEntity createStreamDelta(Integer step, String stepName, String subType, String content, String sessionId) {
        return AgentExecuteResultEntity.builder()
                .type("analysis")
                .subType(subType)
                .step(step)
                .stepName(step != null && step >= 1 && step < STEP_NAMES.length ? STEP_NAMES[step] : null)
                .content(content)
                .streamDelta(true)
                .timestamp(System.currentTimeMillis())
                .sessionId(sessionId)
                .build();
    }

    /**
     * 流式结束：前端结束当前流式消息
     */
    public static AgentExecuteResultEntity createStreamEnd(Integer step, String stepName, String subType, String sessionId) {
        return AgentExecuteResultEntity.builder()
                .type("分析")
                .subType(subType)
                .step(step)
                .stepName(step != null && step >= 1 && step < STEP_NAMES.length ? STEP_NAMES[step] : null)
                .content(null)
                .streamEnd(true)
                .timestamp(System.currentTimeMillis())
                .sessionId(sessionId)
                .build();
    }
}