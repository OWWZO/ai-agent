package org.wwz.ai.infrastructure.dao.reactor.po;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SessionTodoPO {
    private Long id;
    private String sessionId;
    private String taskId;
    private String subject;
    private String description;
    private String activeForm;
    private String owner;
    private String status;
    private String blocksJson;
    private String blockedByJson;
    private String metadataJson;
    private Integer seqNo;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
