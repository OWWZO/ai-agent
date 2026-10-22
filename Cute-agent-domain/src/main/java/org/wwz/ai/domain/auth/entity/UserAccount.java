package org.wwz.ai.domain.auth.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Registered account aggregate root.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserAccount {

    public static final int STATUS_DISABLED = 0;
    public static final int STATUS_ACTIVE = 1;

    private Long id;

    /** Stable account identity propagated to agent ownership columns and JWT sub. */
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

    public boolean isActive() {
        return Integer.valueOf(STATUS_ACTIVE).equals(status);
    }
}
