package org.wwz.ai.infrastructure.dao.reactor;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.wwz.ai.domain.agent.memory.PromptMemoryStreamKey;
import org.wwz.ai.domain.agent.memory.entity.PromptMemoryStream;

import java.time.LocalDateTime;

/**
 * 提示词记忆流头 DAO。
 */
@Mapper
public interface IPromptMemoryStreamDao {

    int insertIgnore(PromptMemoryStream stream);

    PromptMemoryStream queryByKey(@Param("key") PromptMemoryStreamKey key);

    int acquireLease(@Param("key") PromptMemoryStreamKey key,
                     @Param("requestId") String requestId,
                     @Param("now") LocalDateTime now,
                     @Param("leaseExpireAt") LocalDateTime leaseExpireAt);

    int releaseLease(@Param("streamId") Long streamId, @Param("requestId") String requestId);

    int advanceReadyTurn(@Param("streamId") Long streamId,
                         @Param("requestId") String requestId,
                         @Param("expectedLatestTurnSeq") Integer expectedLatestTurnSeq,
                         @Param("nextTurnSeq") Integer nextTurnSeq);
}
