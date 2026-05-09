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

    /**
     * 当前浏览器访客首次命名后的用户名。
     */
    private String username;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer deleted;
}
