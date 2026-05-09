package org.wwz.ai.infrastructure.dao.reactor;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.wwz.ai.infrastructure.dao.po.VisitorIdentityPO;

import java.time.LocalDateTime;

/**
 * 匿名访客 DAO。
 */
@Mapper
public interface IVisitorIdentityDao {

    int insert(VisitorIdentityPO record);

    VisitorIdentityPO queryByTokenDigest(@Param("tokenDigest") String tokenDigest);

    VisitorIdentityPO queryByVisitorId(@Param("visitorId") String visitorId);

    int updateLastSeen(@Param("visitorId") String visitorId,
                       @Param("lastSeenAt") LocalDateTime lastSeenAt,
                       @Param("lastIp") String lastIp,
                       @Param("lastUserAgent") String lastUserAgent);

    int bindUsernameIfAbsent(@Param("visitorId") String visitorId,
                             @Param("username") String username,
                             @Param("updateTime") LocalDateTime updateTime);
}
