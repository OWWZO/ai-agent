package org.wwz.ai.infrastructure.adapter.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.wwz.ai.domain.auth.entity.UserAccount;
import org.wwz.ai.domain.auth.exception.LoginNameAlreadyExistsException;
import org.wwz.ai.domain.auth.repository.IUserAccountRepository;
import org.wwz.ai.infrastructure.dao.IUserAccountDao;
import org.wwz.ai.infrastructure.dao.po.UserAccountPO;

import java.time.LocalDateTime;

/**
 * MyBatis adapter for account persistence.
 */
@Repository
@RequiredArgsConstructor
public class UserAccountRepository implements IUserAccountRepository {

    private final IUserAccountDao userAccountDao;

    @Override
    public UserAccount findById(Long id) {
        UserAccountPO account = userAccountDao.queryById(id);
        return account == null ? null : toDomain(account);
    }

    @Override
    public UserAccount findByUserId(String userId) {
        UserAccountPO account = userAccountDao.queryByUserId(userId);
        return account == null ? null : toDomain(account);
    }

    @Override
    public UserAccount findByLoginName(String loginName) {
        UserAccountPO account = userAccountDao.queryByLoginName(loginName);
        return account == null ? null : toDomain(account);
    }

    @Override
    public UserAccount save(UserAccount account) {
        LocalDateTime now = LocalDateTime.now();
        if (account.getCreateTime() == null) {
            account.setCreateTime(now);
        }
        if (account.getUpdateTime() == null) {
            account.setUpdateTime(now);
        }
        UserAccountPO po = toPO(account);
        try {
            userAccountDao.insert(po);
        } catch (DataIntegrityViolationException e) {
            throw new LoginNameAlreadyExistsException();
        }
        return toDomain(po);
    }

    @Override
    public boolean updatePassword(String userId, String passwordHash, LocalDateTime updateTime) {
        return userAccountDao.updatePassword(userId, passwordHash, updateTime) > 0;
    }

    @Override
    public boolean updateLastLoginAt(String userId, LocalDateTime lastLoginAt) {
        return userAccountDao.updateLastLoginAt(userId, lastLoginAt) > 0;
    }

    private UserAccount toDomain(UserAccountPO po) {
        return UserAccount.builder()
                .id(po.getId())
                .userId(po.getUserId())
                .tokenDigest(po.getTokenDigest())
                .status(po.getStatus())
                .firstSeenAt(po.getFirstSeenAt())
                .lastSeenAt(po.getLastSeenAt())
                .lastIp(po.getLastIp())
                .lastUserAgent(po.getLastUserAgent())
                .username(po.getUsername())
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .deleted(po.getDeleted())
                .loginName(po.getLoginName())
                .passwordHash(po.getPasswordHash())
                .nickname(po.getNickname())
                .role(po.getRole())
                .lastLoginAt(po.getLastLoginAt())
                .build();
    }

    private UserAccountPO toPO(UserAccount account) {
        return UserAccountPO.builder()
                .id(account.getId())
                .userId(account.getUserId())
                .tokenDigest(account.getTokenDigest())
                .status(account.getStatus())
                .firstSeenAt(account.getFirstSeenAt())
                .lastSeenAt(account.getLastSeenAt())
                .lastIp(account.getLastIp())
                .lastUserAgent(account.getLastUserAgent())
                .username(account.getUsername())
                .createTime(account.getCreateTime())
                .updateTime(account.getUpdateTime())
                .deleted(account.getDeleted())
                .loginName(account.getLoginName())
                .passwordHash(account.getPasswordHash())
                .nickname(account.getNickname())
                .role(account.getRole())
                .lastLoginAt(account.getLastLoginAt())
                .build();
    }
}
