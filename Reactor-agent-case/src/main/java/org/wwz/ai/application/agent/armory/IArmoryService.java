package org.wwz.ai.application.agent.armory;

import org.wwz.ai.domain.agent.model.valobj.AiAgentVO;

import java.util.List;

/**
 * Agent 应用层装配接口。
 */
public interface IArmoryService {

    List<AiAgentVO> acceptArmoryAllAvailableAgents();

    void acceptArmoryAgent(String agentId);

    List<AiAgentVO> queryAvailableAgents();

    void acceptArmoryAgentClientModelApi(String apiId);
}
