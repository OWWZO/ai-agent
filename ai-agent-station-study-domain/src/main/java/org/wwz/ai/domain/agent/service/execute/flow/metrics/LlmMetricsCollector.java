package org.wwz.ai.domain.agent.service.execute.flow.metrics;

import org.springframework.ai.chat.model.ChatResponse;
import org.wwz.ai.domain.agent.model.entity.AgentExecuteResultEntity.LlmMetrics;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * LLM 调用指标采集器，用于统计 token 用量和估算成本
 * 
 * <p>设计说明：
 * <ul>
 *   <li>线程安全：使用 volatile + synchronized 确保并发安全</li>
 *   <li>自动统计：通过 MetricsAdvisor 自动从 ChatResponse.metadata.usage 提取 token</li>
 *   <li>成本估算：基于输入/输出 token 数量估算调用成本</li>
 * </ul>
 * 
 * <p>使用方式：
 * <pre>{@code
 * LlmMetricsCollector collector = new LlmMetricsCollector();
 * chatClient.prompt()
 *     .user(message)
 *     .advisors(a -> a.param("llmMetricsCollector", collector))
 *     .call();
 * LlmMetrics metrics = collector.build();
 * }</pre>
 * 
 * @author WWZ
 */
public class LlmMetricsCollector {

    private volatile long inputTokens;
    private volatile long outputTokens;
    private volatile int callCount;
    private final long startTimeMs;

    public LlmMetricsCollector() {
        this.startTimeMs = System.currentTimeMillis();
    }

    /**
     * 从 ChatResponse 累积 token 用量
     * 
     * <p>线程安全：使用 synchronized 确保并发安全
     * 
     * @param response ChatResponse 对象，包含 metadata.usage
     */
    public synchronized void accumulate(ChatResponse response) {
        if (response == null) {
            return;
        }
        
        var metadata = response.getMetadata();
        if (metadata == null) {
            return;
        }
        
        try {
            var usage = metadata.getUsage();
            if (usage != null) {
                Number prompt = usage.getPromptTokens();
                Number completion = usage.getCompletionTokens();
                
                if (prompt != null && prompt.longValue() > 0) {
                    inputTokens += prompt.longValue();
                }
                if (completion != null && completion.longValue() > 0) {
                    outputTokens += completion.longValue();
                }
            }
        } catch (Exception e) {
            // 部分模型可能不返回 usage，忽略异常不影响业务
            // 日志级别较低，避免噪音
        }
        
        callCount++;
    }

    /**
     * 构建 LlmMetrics，含预估成本
     * 参考价格（GPT-4o-mini 等）：输入 $0.15/1M，输出 $0.6/1M
     */
    public LlmMetrics build() {
        long total = inputTokens + outputTokens;
        long durationMs = System.currentTimeMillis() - startTimeMs;

        // 粗略估算：输入约 0.001元/1K tokens，输出约 0.004元/1K tokens
        BigDecimal inputCost = BigDecimal.valueOf(inputTokens).divide(BigDecimal.valueOf(1_000_000), 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(1.0));
        BigDecimal outputCost = BigDecimal.valueOf(outputTokens).divide(BigDecimal.valueOf(1_000_000), 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(4.0));
        BigDecimal estimatedCost = inputCost.add(outputCost).setScale(4, RoundingMode.HALF_UP);

        return LlmMetrics.builder()
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .totalTokens(total)
                .estimatedCost(estimatedCost)
                .totalDurationMs(durationMs)
                .callCount(callCount)
                .build();
    }
}
