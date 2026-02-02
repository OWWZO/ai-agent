package org.wwz.ai.domain.agent.service;

import org.wwz.ai.domain.agent.model.valobj.AiAgentVO;

import java.util.List;

/**
 * 装配接口
 */
public interface IArmoryService {

    List<AiAgentVO> acceptArmoryAllAvailableAgents();

    void acceptArmoryAgent(String agentId);

    List<AiAgentVO> queryAvailableAgents();

    void acceptArmoryAgentClientModelApi(String apiId);

}
