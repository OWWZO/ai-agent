package org.wwz.ai.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 匿名访客持久化对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisitorIdentityPO {

    private Long id;

    private String visitorId;

    private String tokenDigest;

    private Integer status;

    private LocalDateTime firstSeenAt;

    private LocalDateTime lastSeenAt;

    private String lastIp;

    private String lastUserAgent;

    private String username;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer deleted;
}
