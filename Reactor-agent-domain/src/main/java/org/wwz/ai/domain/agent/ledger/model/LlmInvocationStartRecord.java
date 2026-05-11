package org.wwz.ai.domain.agent.ledger.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 创建 LLM 调用的命令对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmInvocationStartRecord {

    private Long runId;

    private String requestId;

    private Integer invocationSeq;

    private String agentName;

    private Integer stepNo;

    private String callKind;

    private Boolean streaming;

    private String modelName;

    private LocalDateTime startedAt;
}
