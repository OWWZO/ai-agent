package org.wwz.ai.domain.agent.reactor.service;

import org.wwz.ai.domain.agent.model.valobj.ConversationRoleVO;
import org.wwz.ai.domain.agent.reactor.entity.AgentConversation;
import org.wwz.ai.domain.agent.reactor.model.history.ConversationTurnDetail;

import java.util.List;

/**
 * Agent会话服务
 */
public interface IAgentConversationService {

    /**
     * 创建会话
     */
    AgentConversation createConversation(String sessionId, String deviceId, String title,
                                         Integer agentType, String productType,
                                         String aiAgentId, String aiAgentNameSnapshot);

    /**
     * 按sessionId查询会话
     */
    AgentConversation getBySessionId(String sessionId);

    /**
     * 按设备/用户归属校验可访问会话。
     */
    AgentConversation getAccessibleConversation(String sessionId, String deviceId, Long userId);

    /**
     * 重命名会话
     */
    void renameConversation(String sessionId, String deviceId, String newTitle);

    /**
     * 软删除会话
     */
    void deleteConversation(String sessionId, String deviceId);

    /**
     * 分页查询会话列表
     */
    List<AgentConversation> listConversations(String deviceId, Long userId, int pageNo, int pageSize);

    /**
     * 查询会话总数
     */
    int countConversations(String deviceId, Long userId);

    /**
     * 加载会话详情(按 turn + events 装配)
     */
    List<ConversationTurnDetail> getConversationTurns(String sessionId, String deviceId, Long userId);

    /**
     * 置顶/取消置顶
     */
    void togglePin(String sessionId, String deviceId, boolean pinned);

    /**
     * 匿名会话迁移到用户
     */
    int migrateToUser(String deviceId, Long userId);

    /**
     * 绑定 chat 角色
     */
    AgentConversation bindChatRole(AgentConversation conversation, String aiAgentId, String aiAgentNameSnapshot);

    /**
     * 构建会话角色摘要
     */
    ConversationRoleVO buildConversationRole(AgentConversation conversation);
}
