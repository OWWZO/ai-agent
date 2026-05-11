package org.wwz.ai.domain.agent.runtime.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.wwz.ai.domain.agent.runtime.enums.AgentState;

import java.util.ArrayList;
import java.util.List;

/**
 * 并发子任务最小执行结果。
 * 只承载父执行器回流所需的文本结果、memory 增量和 child 状态。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubTaskExecutionResult {

    /**
     * 当前子任务文本，便于日志和测试定位。
     */
    private String task;

    /**
     * child executor 的最终文本结果。
     */
    private String taskResult;

    /**
     * child executor 执行完成后的最终状态。
     */
    private AgentState state;

    /**
     * 相比父 memory 快照新增的消息增量。
     */
    @Builder.Default
    private List<Message> memoryIncrementMessages = new ArrayList<>();
}
