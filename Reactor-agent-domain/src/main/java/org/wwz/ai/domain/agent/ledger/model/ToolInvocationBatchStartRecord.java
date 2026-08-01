package org.wwz.ai.domain.agent.ledger.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 批量预登记工具调用的命令对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolInvocationBatchStartRecord {

    private Long runId;

    private String requestId;

    private Long llmInvocationId;

    private String agentName;

    private Integer stepNo;

    private List<Item> items;

    /**
     * 单个工具调用项。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {

        private String toolCallId;

        /** 父 Agent 工具 toolCallId（子 Agent 内嵌工具） */
        private String parentToolCallId;

        private String subAgentId;

        private String subAgentType;

        private String subAgentDescription;

        private Integer dispatchIndex;

        private String toolName;

        private String toolProvider;

        private String inputJson;

        private LocalDateTime startedAt;
    }
}
