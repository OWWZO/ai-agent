package org.wwz.ai.domain.agent.memory.ltm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户级长期记忆策展条目。
 *
 * status 区分可检索、待审核和删除状态；source 字段保留写入来源，便于后台审核和
 * 记忆回溯，不把策展内容混入 Execution Ledger 的运行事件。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CuratedMemoryEntry {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_PENDING = "PENDING_APPROVAL";
    public static final String STATUS_DELETED = "DELETED";

    private Long id;
    private Long streamId;
    private LtmOwnerType ownerType;
    private String ownerId;
    private CuratedMemoryScope scope;
    private String content;
    private String status;
    private String sourceSessionId;
    private String sourceRequestId;
    private String writeOrigin;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
