package org.wwz.ai.infrastructure.dao.reactor;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.wwz.ai.domain.agent.memory.entity.PromptMemoryMessageRow;

import java.util.List;

/**
 * 提示词记忆消息 DAO。
 */
@Mapper
public interface IPromptMemoryMessageDao {

    int batchInsert(@Param("turnId") Long turnId, @Param("messages") List<PromptMemoryMessageRow> messages);

    List<PromptMemoryMessageRow> queryReadyByStreamId(@Param("streamId") Long streamId);
}
