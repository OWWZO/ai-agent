package org.wwz.ai.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.wwz.ai.infrastructure.dao.po.SubAgentDefinitionPO;

import java.util.List;

/**
 * 可配置子 Agent 定义 DAO。
 */
@Mapper
public interface ISubAgentDefinitionDao {

    List<SubAgentDefinitionPO> queryEnabled();

    List<SubAgentDefinitionPO> queryAll();

    SubAgentDefinitionPO queryByAgentKey(@Param("agentKey") String agentKey);

    int insert(SubAgentDefinitionPO po);

    int updateByAgentKey(SubAgentDefinitionPO po);

    int softDeleteByAgentKey(@Param("agentKey") String agentKey);
}
