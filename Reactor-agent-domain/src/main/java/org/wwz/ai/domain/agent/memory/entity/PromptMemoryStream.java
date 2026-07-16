package org.wwz.ai.domain.agent.memory.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 提示词记忆流头，维护发布轮次与短时写租约。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptMemoryStream {

    private Long id;
    private String sessionId;
    private String memoryScope;
    private String promptContractId;
    private String toolContractId;
    private Integer latestTurnSeq;
    private String activeRequestId;
    private LocalDateTime leaseExpireAt;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
