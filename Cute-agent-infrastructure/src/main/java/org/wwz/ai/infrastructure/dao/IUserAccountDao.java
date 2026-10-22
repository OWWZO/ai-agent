package org.wwz.ai.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.wwz.ai.infrastructure.dao.po.UserAccountPO;

/**
 * user_account data access object.
 */
@Mapper
public interface IUserAccountDao {

    int insert(UserAccountPO account);

    UserAccountPO queryById(@Param("id") Long id);

    UserAccountPO queryByUserId(@Param("userId") String userId);

    UserAccountPO queryByLoginName(@Param("loginName") String loginName);

    int updatePassword(@Param("userId") String userId,
                       @Param("passwordHash") String passwordHash,
                       @Param("updateTime") java.time.LocalDateTime updateTime);

    int updateLastLoginAt(@Param("userId") String userId,
                          @Param("lastLoginAt") java.time.LocalDateTime lastLoginAt);

}
