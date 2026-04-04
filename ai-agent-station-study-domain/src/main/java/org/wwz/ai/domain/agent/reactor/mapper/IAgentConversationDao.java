package org.wwz.ai.domain.agent.reactor.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.wwz.ai.domain.agent.reactor.entity.AgentConversation;

import java.util.List;

/**
 * AI Agent 会话表 DAO
 */
@Mapper
public interface IAgentConversationDao {

    int insert(AgentConversation conversation);

    int updateById(AgentConversation conversation);

    /**
     * 递增消息轮数
     */
    int incrementMessageCount(@Param("id") Long id);

    int softDeleteBySessionId(@Param("sessionId") String sessionId, @Param("deviceId") String deviceId);

    AgentConversation queryById(@Param("id") Long id);

    AgentConversation queryBySessionId(@Param("sessionId") String sessionId);

    /**
     * 分页查询会话列表(按设备ID)
     */
    List<AgentConversation> queryByDeviceId(@Param("deviceId") String deviceId,
                                            @Param("offset") int offset,
                                            @Param("limit") int limit);

    /**
     * 查询会话总数(按设备ID)
     */
    int countByDeviceId(@Param("deviceId") String deviceId);

    /**
     * 分页查询会话列表(按用户ID或设备ID)
     */
    List<AgentConversation> queryByUserIdOrDeviceId(@Param("userId") Long userId,
                                                     @Param("deviceId") String deviceId,
                                                     @Param("offset") int offset,
                                                     @Param("limit") int limit);

    int countByUserIdOrDeviceId(@Param("userId") Long userId, @Param("deviceId") String deviceId);

    /**
     * 分页查询会话列表(不筛选用户/设备)
     */
    List<AgentConversation> queryAll(@Param("offset") int offset, @Param("limit") int limit);

    /**
     * 查询会话总数(不筛选用户/设备)
     */
    int countAll();

    /**
     * 将设备的匿名会话迁移到用户
     */
    int migrateDeviceToUser(@Param("deviceId") String deviceId, @Param("userId") Long userId);
}
