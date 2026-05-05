package org.wwz.ai.domain.agent.adapter.repository;

import org.wwz.ai.domain.agent.model.valobj.*;

import java.util.List;
import java.util.Map;

/**
 * AiAgent 仓储接口
 */
public interface IAgentRepository {

    List<AiClientApiVO> queryAiClientApiVOListByClientIds(List<String> clientIdList);

    List<AiClientModelVO> AiClientModelVOByClientIds(List<String> clientIdList);

    List<AiClientToolMcpVO> AiClientToolMcpVOByClientIds(List<String> clientIdList);

    /**
     * 查询所有启用的 MCP 配置。
     *
     * @return 启用的 MCP 配置列表
     */
    List<AiClientToolMcpVO> queryEnabledAiClientToolMcpVOList();

    /**
     * 查询指定客户端关联的启用 MCP ID 列表。
     *
     * @param clientIdList 客户端 ID 列表
     * @return key 为 clientId，value 为启用的 mcpId 列表
     */
    Map<String, List<String>> queryEnabledClientMcpIdMap(List<String> clientIdList);

    List<AiClientSystemPromptVO> AiClientSystemPromptVOByClientIds(List<String> clientIdList);

    Map<String, AiClientSystemPromptVO> queryAiClientSystemPromptMapByClientIds(List<String> clientIdList);

    List<AiClientAdvisorVO> AiClientAdvisorVOByClientIds(List<String> clientIdList);

    List<AiClientVO> AiClientVOByClientIds(List<String> clientIdList);

    List<AiClientApiVO> queryAiClientApiVOListByModelIds(List<String> modelIdList);

    List<AiClientModelVO> AiClientModelVOByModelIds(List<String> modelIdList);

    Map<String, AiAgentClientFlowConfigVO> queryAiAgentClientFlowConfig(String aiAgentId);

    AiAgentVO queryAiAgentByAgentId(String aiAgentId);

    List<AiAgentClientFlowConfigVO> queryAiAgentClientsByAgentId(String aiAgentId);

    List<AiAgentTaskScheduleVO> queryAllValidTaskSchedule();

    List<Long> queryAllInvalidTaskScheduleIds();

    void createTagOrder(AiRagOrderVO aiRagOrderVO);

    /**
     * 查询可用的智能体列表
     * @return 可用的智能体列表
     */
    List<AiAgentVO> queryAvailableAgents();

    /**
     * 查询可用的 Fix 角色列表
     */
    List<AiAgentVO> queryAvailableFixRoles();

    /**
     * 按角色ID查询可用 Fix 角色
     */
    AiAgentVO queryAvailableFixRoleByAgentId(String aiAgentId);

    List<AiClientApiVO> queryAiClientApiVOListByApiIds(List<String> apiIdList);

}
