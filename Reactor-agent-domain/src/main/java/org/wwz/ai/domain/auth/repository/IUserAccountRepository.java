package org.wwz.ai.domain.auth.repository;

import org.wwz.ai.domain.auth.entity.UserAccount;

import java.time.LocalDateTime;

/**
 * Account persistence port.
 */
public interface IUserAccountRepository {

    UserAccount findById(Long id);

    UserAccount findByUserId(String userId);

    UserAccount findByLoginName(String loginName);

    UserAccount save(UserAccount account);

    boolean updatePassword(String userId, String passwordHash, LocalDateTime updateTime);

    boolean updateLastLoginAt(String userId, LocalDateTime lastLoginAt);

}
