package org.wwz.ai.domain.agent.memory.ltm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
