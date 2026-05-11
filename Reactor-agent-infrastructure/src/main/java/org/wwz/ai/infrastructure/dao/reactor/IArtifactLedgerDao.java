package org.wwz.ai.infrastructure.dao.reactor;

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

    List<ArtifactRecord> queryByRunIds(@Param("runIds") List<Long> runIds);

    List<ArtifactRecord> queryByToolInvocationIds(@Param("toolInvocationIds") List<Long> toolInvocationIds);

    List<ArtifactRecord> queryInputArtifactsByRunIds(@Param("runIds") List<Long> runIds);

    List<ArtifactRecord> queryOutputArtifactsByToolInvocationId(@Param("toolInvocationId") Long toolInvocationId);

    List<ArtifactRecord> queryOutputArtifactsByRunIdAndToolCallId(@Param("runId") Long runId,
                                                                  @Param("toolCallId") String toolCallId);

    List<ArtifactRecord> queryOutputArtifactsByRequestIdAndToolCallId(@Param("requestId") String requestId,
                                                                      @Param("toolCallId") String toolCallId);
}
