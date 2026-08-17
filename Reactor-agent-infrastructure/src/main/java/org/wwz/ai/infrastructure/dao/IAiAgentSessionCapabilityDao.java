package org.wwz.ai.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.wwz.ai.infrastructure.dao.po.AiAgentSessionCapability;

import java.util.List;

@Mapper
public interface IAiAgentSessionCapabilityDao {

    List<AiAgentSessionCapability> listBySessionId(@Param("sessionId") String sessionId);

    int upsert(AiAgentSessionCapability row);
}
