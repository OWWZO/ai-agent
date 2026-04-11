package org.wwz.ai.domain.agent.service;

import org.wwz.ai.domain.agent.model.valobj.ConversationRoleVO;
import org.wwz.ai.domain.agent.model.valobj.FixRoleVO;
import org.wwz.ai.domain.agent.reactor.entity.AgentConversation;

import java.util.List;

/**
 * Fix 角色领域服务
 */
public interface IFixRoleService {

    /**
     * 查询可用角色列表
     */
    List<FixRoleVO> queryAvailableRoles();

    /**
     * 查询默认角色
     */
    FixRoleVO queryDefaultRole();

    /**
     * 查询指定角色
     */
    FixRoleVO queryRole(String aiAgentId);

    /**
     * 构建会话角色摘要
     */
    ConversationRoleVO buildConversationRole(AgentConversation conversation);
}
