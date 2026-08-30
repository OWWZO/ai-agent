package org.wwz.ai.domain.agent.adapter.repository;

import org.wwz.ai.domain.agent.model.valobj.AiClientToolMcpVO;

import java.util.List;

/**
 * MCP 配置读取端口。
 *
 * <p>Agent 角色、Workflow、客户端装配和定时任务配置已下线；运行时只保留
 * MCP 全局配置读取能力。</p>
 */
public interface IAgentRepository {

    List<AiClientToolMcpVO> queryEnabledAiClientToolMcpVOList();

}
