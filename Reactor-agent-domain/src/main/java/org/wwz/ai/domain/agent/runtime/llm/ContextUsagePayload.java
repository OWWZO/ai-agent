package org.wwz.ai.domain.agent.runtime.llm;

import lombok.Builder;
import lombok.Data;

/**
 * 上下文占用分段（驱动输入框 ContextRing）。
 * 单位 token；max 为模型窗口；used 优先上游真实 prompt_tokens。
 */
@Data
@Builder
public class ContextUsagePayload {

    private int sys;
    private int tools;
    private int history;
    private int files;
    private int max;
    /** 估算总和或上游 prompt_tokens */
    private int used;
    private Integer promptTokens;
    private Integer completionTokens;
    /** estimate | measured */
    private String source;

    public static ContextUsagePayload fromEstimate(TokenCounter.PromptEstimate est, int max) {
        if (est == null) {
            return ContextUsagePayload.builder().max(Math.max(max, 1)).source("estimate").build();
        }
        int used = est.getEstimatedTotalTokens();
        return ContextUsagePayload.builder()
                .sys(est.getSystemTokens())
                .tools(est.getToolTokens())
                .history(est.getMessageTokens())
                .files(0)
                .max(Math.max(max, 1))
                .used(used)
                .source("estimate")
                .build();
    }
}
