package org.wwz.ai.domain.agent.service.execute.flow.metrics;

import org.springframework.ai.chat.model.ChatResponse;
import org.wwz.ai.domain.agent.model.entity.AutoAgentExecuteResultEntity.LlmMetrics;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * LLM 调用指标采集器，用于统计 token 用量和估算成本
 */
public class LlmMetricsCollector {

    private long inputTokens;
    private long outputTokens;
    private int callCount;
    private long startTimeMs;

    public LlmMetricsCollector() {
        this.startTimeMs = System.currentTimeMillis();
    }

    /**
     * 从 ChatResponse 累积 token 用量
     */
    public void accumulate(ChatResponse response) {
        if (response == null) return;
        var metadata = response.getMetadata();
        if (metadata == null) return;
        try {
            var usage = metadata.getUsage();
            if (usage != null) {
                Number prompt = usage.getPromptTokens();
                Number completion = usage.getCompletionTokens();
                if (prompt != null) inputTokens += prompt.longValue();
                if (completion != null) outputTokens += completion.longValue();
            }
        } catch (Exception ignored) {
            // 部分模型可能不返回 usage
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
