package org.wwz.ai.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.wwz.ai.infrastructure.dao.po.AiAgentBrowserSession;

import java.time.LocalDateTime;

/**
 * Owner-to-Agent Browser session mapping DAO.
 */
@Mapper
public interface IAiAgentBrowserSessionDao {

    int insert(AiAgentBrowserSession row);

    AiAgentBrowserSession queryByOwnerKey(@Param("ownerKey") String ownerKey);

    AiAgentBrowserSession queryByAgentBrowserName(@Param("agentBrowserName") String agentBrowserName);

    int updateSession(AiAgentBrowserSession row);

    int updateLastUsedAt(@Param("ownerKey") String ownerKey, @Param("lastUsedAt") LocalDateTime lastUsedAt);

    int deleteByOwnerKey(@Param("ownerKey") String ownerKey);
}
