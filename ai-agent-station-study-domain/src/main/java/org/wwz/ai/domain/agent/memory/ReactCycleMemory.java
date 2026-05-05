package org.wwz.ai.domain.agent.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 单次 LLM 调用对应的一次 ReAct 循环。
 * 一个 cycle 下面再挂载本轮关联的工具调用明细。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReactCycleMemory {

    private Long runId;

    private String requestId;

    private Long llmInvocationId;

    private Integer invocationSeq;

    private String agentName;

    private Integer stepNo;

    private String thoughtContent;

    @Builder.Default
    private List<ToolCallMemory> toolCalls = new ArrayList<>();
}
