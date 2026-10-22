package org.wwz.ai.infrastructure.dao.cute;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.wwz.ai.domain.agent.ledger.entity.ArtifactRecord;

import java.util.List;

/**
 * 产物账本 DAO。
 */
@Mapper
public interface IArtifactLedgerDao {

    int batchInsertArtifacts(@Param("records") List<ArtifactRecord> records);

    List<ArtifactRecord> queryByRunId(@Param("runId") Long runId);

    /**
     * 子 Agent 产物按血缘重键到父 Agent 卡 invocation 的单 run 查询。
     */
    List<ArtifactRecord> queryByRunIdWithSubagentFold(@Param("runId") Long runId);

    List<ArtifactRecord> queryByRunIds(@Param("runIds") List<Long> runIds);

    /**
     * 子 Agent 产物按血缘重键到父 Agent 卡 invocation 的批量查询。
     */
    List<ArtifactRecord> queryByRunIdsWithSubagentFold(@Param("runIds") List<Long> runIds);

    List<ArtifactRecord> queryBySessionId(@Param("sessionId") String sessionId);

    List<ArtifactRecord> queryByToolInvocationIds(@Param("toolInvocationIds") List<Long> toolInvocationIds);

    List<ArtifactRecord> queryInputArtifactsByRunIds(@Param("runIds") List<Long> runIds);

    List<ArtifactRecord> queryOutputArtifactsByToolInvocationId(@Param("toolInvocationId") Long toolInvocationId);

    List<ArtifactRecord> queryOutputArtifactsByRunIdAndToolCallId(@Param("runId") Long runId,
                                                                  @Param("toolCallId") String toolCallId);

    List<ArtifactRecord> queryOutputArtifactsByRequestIdAndToolCallId(@Param("requestId") String requestId,
                                                                      @Param("toolCallId") String toolCallId);
}
