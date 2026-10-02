package org.wwz.ai.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.wwz.ai.infrastructure.dao.po.AiAgentKernelBrowserSession;

import java.time.LocalDateTime;

/**
 * Owner-to-Kernel Browser session mapping DAO.
 */
@Mapper
public interface IAiAgentKernelBrowserSessionDao {

    int insert(AiAgentKernelBrowserSession row);

    AiAgentKernelBrowserSession queryByOwnerKey(@Param("ownerKey") String ownerKey);

    AiAgentKernelBrowserSession queryByKernelBrowserName(@Param("kernelBrowserName") String kernelBrowserName);

    int updateSession(AiAgentKernelBrowserSession row);

    int updateLastUsedAt(@Param("ownerKey") String ownerKey, @Param("lastUsedAt") LocalDateTime lastUsedAt);

    int deleteByOwnerKey(@Param("ownerKey") String ownerKey);

    Integer getLock(@Param("name") String name, @Param("timeoutSeconds") int timeoutSeconds);

    Integer releaseLock(@Param("name") String name);
}
