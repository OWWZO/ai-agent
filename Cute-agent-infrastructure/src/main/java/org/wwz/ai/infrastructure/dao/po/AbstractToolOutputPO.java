package org.wwz.ai.infrastructure.dao.po;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 输出表公共字段。
 * 仅在基础设施层内部用于序列化和反序列化，不向领域层泄漏。
 */
@Data
public abstract class AbstractToolOutputPO {

    private Long id;

    private Long toolInvocationId;

    private Long runId;

    private String requestId;

    private String requestSource;

    private String sessionId;

    private String toolCallId;

    private Integer status;

    private String errorMsg;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
