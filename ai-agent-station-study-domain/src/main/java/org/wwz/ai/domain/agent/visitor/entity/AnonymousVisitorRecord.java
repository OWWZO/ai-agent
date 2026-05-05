package org.wwz.ai.domain.agent.visitor.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 匿名访客记录。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnonymousVisitorRecord {

    private Long id;

    private String visitorId;

    private String tokenDigest;

    private Integer status;

    private LocalDateTime firstSeenAt;

    private LocalDateTime lastSeenAt;

    private String lastIp;

    private String lastUserAgent;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer deleted;
}
