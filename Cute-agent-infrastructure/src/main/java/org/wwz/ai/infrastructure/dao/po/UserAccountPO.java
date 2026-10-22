package org.wwz.ai.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Persistence object for user_account.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserAccountPO {

    private Long id;

    private String userId;

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

    private String loginName;

    private String passwordHash;

    private String nickname;

    private String role;

    private LocalDateTime lastLoginAt;
}
